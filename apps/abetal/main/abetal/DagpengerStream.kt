package abetal

import libs.kafka.*
import libs.kafka.stream.MappedStream
import libs.utils.appLog
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.trygdeetaten.skjema.oppdrag.Oppdrag

/**
 * Dagpenger sender hele saken sin hver gang, som inneholder en eller fler meldeperioder.
 * Hos oss er en meldeperiode for en stønadstype = en utbetaling. 
 * Dvs at fler stønadstyper innenfor en meldeperiode blir til forskjellige utbetalinger.
 * Alle utbetalingene blir så transformert til Oppdrag eller Simulering (dryrun).
 * Fordi en utbetaling blir til ett oppdrag/simulering blir oppdrag/simulering akkumulert til ett.
 * Aggregatet som nå er ett oppdrag/simulering blir sendt til Oppdrag UR.
 * Alle utbetalingene som ligger i oppdraget/simuleringen legges på pending-utbetalinger. 
 * Ved suksess flyttes pending-utbetalinger til utbetalinger, 
 */
fun Topology.dagpengerStream(
    utbetalinger: GlobalKTable<String, Utbetaling>,
    saker: KTable<SakKey, Set<UtbetalingId>>,
    kafka: Streams,
) {
    val storeSupplier = { kafka.getStore(Stores.utbetalinger)  }

    consume(Topics.dp)
        .repartition(Topics.dp, 3, "from-${Topics.dp.name}")
        .merge(consume(Topics.dpIntern))
        .map { key, dp -> DpTuple(key, dp) }
        .rekey { (_, dp) -> SakKey(SakId(dp.sakId), Fagsystem.DAGPENGER) }
        .leftJoin(Serde.json(), Serde.json(), saker, "dptuple-leftjoin-saker")
        .peek { key, _, saker -> kafkaLog.info("joined with saker on key:$key. Uids: $saker") }
        .includeHeader(FS_KEY) { Fagsystem.DAGPENGER.name }
        .branch(Guard::ifNoMeldeperiode, Guard::replyOk)
        .default {
            this.map(::splitToDomain)
                .rekey { _, dtos -> dtos.first().originalKey } // alle har samme uid
                .map { _, utbetalinger ->
                    Result.catch {
                        val store = storeSupplier()
                        kafkaLog.info("trying to join ${utbetalinger.size} utbetalinger")
                        val aggregate = utbetalinger.map { new ->
                            val prev = store.getOrNull(new.uid.toString())
                            kafkaLog.info("key ${new.uid} | found previous in store: ${prev != null}")
                            StreamsPair(new, prev)
                        }
                        val oppdragToUtbetalinger = AggregateService.utledOppdrag(aggregate.filter { (new, _) -> !new.dryrun })
                        val simuleringer = AggregateService.utledSimulering(aggregate.filter { (new, _) -> new.dryrun })
                        oppdragToUtbetalinger to simuleringer
                    }
                }
                .branch(Result<*, *>::isErr, Aggregated::replyError) 
                .default {
                    val result = this.map { it.unwrap() }
                    Aggregated.saveUtbetalingerAsPending(result)
                    Aggregated.sendSimulering(result)
                    Aggregated.sendOppdrag(result)
                    Aggregated.replyOkIfIdempotent(result)
                    Aggregated.savePendingUids(result)
                }
        }
}

/**
 * Dagpenger har ikke alltid meldeperioder, 
 * da avbryter vi og svarer med OK med en gang.
 **/
private object Guard {
    fun ifNoMeldeperiode(pair: StreamsPair<DpTuple, Set<UtbetalingId>?>): Boolean {
        val (utbetalinger, saker) = pair.left.value.utbetalinger to pair.right
        return utbetalinger.isEmpty() && saker.isNullOrEmpty()
    }

    fun replyOk(branch: MappedStream<SakKey, StreamsPair<DpTuple, Set<UtbetalingId>?>>) {
        branch.rekey { (dpTuple, _) -> dpTuple.key }
            .map { StatusReply.ok() }
            .produce(Topics.status)
    }
}

typealias Aggregate = Pair<List<Pair<Oppdrag, List<Utbetaling>>>, List<SimulerBeregningRequest>>

private object Aggregated {
    fun replyError(branch: MappedStream<String, Result<Aggregate, StatusReply>>) {
        branch
            .map { it.unwrapErr() }
            .produce(Topics.status)
    }

    fun replyOkIfIdempotent(result: MappedStream<String, Aggregate>) {
        result
            .filter { (oppdragToUtbetalinger, simuleringer) -> oppdragToUtbetalinger.isEmpty() && simuleringer.isEmpty() }
            .map { StatusReply.ok() }
            .produce(Topics.status)

    }

    fun sendOppdrag(result: MappedStream<String, Aggregate>) {
        val oppdrag = result.flatMap { (oppdragToUtbetalinger, _) -> oppdragToUtbetalinger.map { it.first } }
        oppdrag.produce(Topics.oppdrag)
        oppdrag.map(StatusReply::mottatt).produce(Topics.status)
    }

    fun sendSimulering(result: MappedStream<String, Aggregate>) {
        result
            .flatMap { (_, simuleringer) -> simuleringer }
            .produce(Topics.simulering)
    }

    fun saveUtbetalingerAsPending(result: MappedStream<String, Aggregate>) {
        result
            .flatMapKeyAndValue { _, (oppdragToUtbetalinger, _) ->
                oppdragToUtbetalinger.flatMap { agg -> 
                    agg.second.map { KeyValue(it.uid.toString(), it) } 
                }
            }
            .produce(Topics.pendingUtbetalinger)
    }

    fun savePendingUids(result: MappedStream<String, Aggregate>) {
        result
            .flatMapKeyAndValue { transactionId, (oppdragToUtbetalinger, _) ->
                oppdragToUtbetalinger.map { (oppdrag, utbetalinger) ->
                    val uids = utbetalinger.map { u -> u.uid.toString() }
                    KeyValue(oppdrag, PKs(transactionId, uids))
                }
            }
            .produce(Topics.fk)
    }
}

private fun splitToDomain(
    sakKey: SakKey,
    streamsPair: StreamsPair<DpTuple, Set<UtbetalingId>?>,
): List<Utbetaling> {
    val (tuple, uids) = streamsPair
    val (dpKey, dpUtbetaling) = tuple
    val dryrun = dpUtbetaling.dryrun
    val personident = dpUtbetaling.ident
    val behandlingId = dpUtbetaling.behandlingId
    val periodetype = Periodetype.UKEDAG
    val vedtakstidspunktet = dpUtbetaling.vedtakstidspunktet
    val beslutterId = dpUtbetaling.beslutter ?: "dagpenger"
    val saksbehandler = dpUtbetaling.saksbehandler ?: "dagpenger"
    val utbetalingerPerMeldekort: MutableList<Pair<UtbetalingId, DpUtbetaling?>> = dpUtbetaling
        .utbetalinger
        .groupBy { it.meldeperiode to it.stønadstype() }
        .map { (group, utbetalinger) ->
            val (meldeperiode, stønadstype) = group
            dpUId(dpUtbetaling.sakId, meldeperiode, stønadstype) to dpUtbetaling.copy(utbetalinger = utbetalinger)
        }
        .toMutableList()

    if (uids != null) {
        val dpUids = utbetalingerPerMeldekort.map { (dpUid, _) -> dpUid }
        val missingMeldeperioder = uids.filter { it !in dpUids }.map { it to null }
        utbetalingerPerMeldekort.addAll(missingMeldeperioder)
    }

    return utbetalingerPerMeldekort.map { (uid, tsUtbetaling) ->
        when (tsUtbetaling) {
            null -> fakeDelete(
                dryrun = dryrun,
                originalKey = dpKey,
                sakId = sakKey.sakId,
                uid = uid,
                fagsystem = Fagsystem.DAGPENGER,
                stønad = StønadTypeDagpenger.DAGPENGER, // Dette er en placeholder
                beslutterId = Navident(beslutterId),
                saksbehandlerId = Navident(saksbehandler),
                personident = Personident(personident),
                behandlingId = BehandlingId(behandlingId),
                periodetype = periodetype,
                vedtakstidspunkt = vedtakstidspunktet,
            ).also { appLog.debug("creating a fake delete to force-trigger a join with existing utbetaling") }

            else -> toDomain(dpKey, tsUtbetaling, uids, uid)
        }
    }
}

