package abetal

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import libs.kafka.*
import libs.kafka.stream.MappedStream
import libs.utils.appLog
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.trygdeetaten.skjema.oppdrag.Oppdrag

const val FS_KEY = "fagsystem"

object Topics {
    val dp = Topic("teamdagpenger.utbetaling.v1", json<DpUtbetaling>())
    val aap = Topic("aap.utbetaling.v1", json<AapUtbetaling>())
    val ts = Topic("tilleggsstonader.utbetaling.v1", json<TsDto>())
    // TODO: rename denne til tpUtbetalinger når ts har laget topic
    val tp = Topic("helved.utbetalinger-tp.v1", json<TpUtbetaling>())
    val utbetalinger = Topic("helved.utbetalinger.v1", json<Utbetaling>())
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val simulering = Topic("helved.simuleringer.v1", jaxb<SimulerBeregningRequest>())
    val status = Topic("helved.status.v1", json<StatusReply>())
    val saker = Topic("helved.saker.v1", jsonjsonSet<SakKey, UtbetalingId>())
    val pendingUtbetalinger = Topic("helved.pending-utbetalinger.v1", json<Utbetaling>())
    val fk = Topic("helved.fk.v1", Serdes(XmlSerde.xml<Oppdrag>(), JsonSerde.jackson<PKs>()))
    val dpIntern = Topic("helved.utbetalinger-dp.v1", json<DpUtbetaling>())
    val aapIntern = Topic("helved.utbetalinger-aap.v1", json<AapUtbetaling>())
    val tsIntern = Topic("helved.utbetalinger-ts.v1", json<TsDto>())
    val historisk = Topic("historisk.utbetaling.v1", json<HistoriskUtbetaling>())
    val historiskIntern = Topic("helved.utbetalinger-historisk.v1", json<HistoriskUtbetaling>())
}

object Tables {
    val utbetalinger = Table(Topics.utbetalinger, stateStoreName = "${Topics.utbetalinger.name}-state-store-v4")
    val pendingUtbetalinger = Table(Topics.pendingUtbetalinger, stateStoreName = "${Topics.pendingUtbetalinger.name}-state-store-v4")
    val saker = Table(Topics.saker)
    val fk = Table(Topics.fk, stateStoreName = "${Topics.fk.name}-state-store-v4")
}

object Stores {
    val utbetalinger = Store(Tables.utbetalinger)
}

fun createTopology(kafka: Streams): Topology = topology {
    val utbetalinger = globalKTable(Tables.utbetalinger, materializeWithTrace = false)
    val pendingUtbetalinger = consume(Tables.pendingUtbetalinger, materializeWithTrace = false)
    val saker = consume(Tables.saker)
    val fks = consume(Tables.fk, materializeWithTrace = false)
    dpStream(utbetalinger, saker, kafka)
    aapStream(utbetalinger, saker, kafka)
    tsStream(utbetalinger, saker, kafka)
    tpStream(utbetalinger, saker, kafka)
    historiskStream(utbetalinger, saker, kafka)
    successfulUtbetalingStream(fks, pendingUtbetalinger)
}

data class SakKey(val sakId: SakId, val fagsystem: Fagsystem)
data class PKs(val originalKey: String, val uids: List<String>)
data class AapTuple(val key: String, val value: AapUtbetaling)
data class DpTuple(val key: String, val value: DpUtbetaling)
data class TsTuple(val key: String, val value: TsDto)
data class HistoriskTuple(val key: String, val value: HistoriskUtbetaling)
data class TpTuple(val key: String, val value: TpUtbetaling)


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
fun Topology.dpStream(
    utbetalinger: GlobalKTable<String, Utbetaling>,
    saker: KTable<SakKey, Set<UtbetalingId>>,
    kafka: Streams,
) {
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
            this
                .map { sakKey, (req, uids) -> DpDto.splitToDomain(sakKey.sakId, req.key, req.value, uids) }
                .rekey { _, dtos -> dtos.first().originalKey }
                .map { _, utbetalinger ->
                    Result.catch {
                        val store = kafka.getStore(Stores.utbetalinger)
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
                .branch(Result<*, *>::isErr, ::replyError)
                .default {
                    val result = this.map { it.unwrap() }
                    result.saveUtbetalingerAsPending()
                    result.sendSimulering()
                    result.sendOppdrag()
                    result.replyOkIfIdempotent()
                    result.savePendingUids()
                }
        }
}

fun Topology.aapStream(
    utbetalinger: GlobalKTable<String, Utbetaling>,
    saker: KTable<SakKey, Set<UtbetalingId>>,
    kafka: Streams,
) {
    consume(Topics.aap)
        .repartition(Topics.aap, 3, "from-${Topics.aap.name}")
        .merge(consume(Topics.aapIntern))
        .map { key, aap -> AapTuple(key, aap) }
        .rekey { (_, aap) -> SakKey(SakId(aap.sakId), Fagsystem.AAP) }
        .leftJoin(Serde.json(), Serde.json(), saker, "aaptuple-leftjoin-saker")
        .peek { key, _, saker -> kafkaLog.info("joined with saker on key:$key. Uids: $saker") }
        .includeHeader(FS_KEY) { Fagsystem.AAP.name }
        .map { sakKey, req, uids -> AapDto.splitToDomain(sakKey.sakId, req.key, req.value, uids) }
        .rekey { _, dtos -> dtos.first().originalKey }
        .map { value ->
            Result.catch {
                val store = kafka.getStore(Stores.utbetalinger)
                val aggregate = value.map { new ->
                    val prev = store.getOrNull(new.uid.toString())
                    StreamsPair(new, prev)
                }
                val oppdragToUtbetalinger = AggregateService.utledOppdrag(aggregate.filter { (new, _) -> !new.dryrun })
                val simuleringer = AggregateService.utledSimulering(aggregate.filter { (new, _) -> new.dryrun })
                oppdragToUtbetalinger to simuleringer
            }
        }
        .branch(Result<*, *>::isErr, ::replyError)
        .default {
            val result = this.map { it.unwrap() }
            result.saveUtbetalingerAsPending()
            result.sendSimulering()
            result.sendOppdrag()
            result.replyOkIfIdempotent()
            result.savePendingUids()
        }
}

fun Topology.tsStream(
    utbetalinger: GlobalKTable<String, Utbetaling>,
    saker: KTable<SakKey, Set<UtbetalingId>>,
    kafka: Streams,
) {
    consume(Topics.ts)
        .repartition(Topics.ts, 3, "from-${Topics.ts.name}")
        .merge(consume(Topics.tsIntern))
        .map { key, ts -> TsTuple(key, ts) }
        .rekey { (_, ts) -> SakKey(SakId(ts.sakId), Fagsystem.TILLEGGSSTØNADER) }
        .leftJoin(Serde.json(), Serde.json(), saker, "tstuple-leftjoin-saker")
        .peek { key, _, saker -> kafkaLog.info("joined with saker on key:$key. Uids: $saker") }
        .includeHeader(FS_KEY) { Fagsystem.TILLEGGSSTØNADER.name }
        .branch(Guard::ifNoUtbetalinger, Guard::replyOkTs)
        .default {
            this
                .map { sakKey, (req, uids) -> TsDto.toDomain(sakKey.sakId,req.key, req.value, uids) }
                .rekey { dtos -> dtos.first().originalKey }
                .map { utbetalinger ->
                    Result.catch {
                        val store = kafka.getStore(Stores.utbetalinger)
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
                .branch(Result<*, *>::isErr, ::replyError)
                .default {
                    val result = this.map { it.unwrap() }
                    result.saveUtbetalingerAsPending()
                    result.sendSimulering()
                    result.sendOppdrag()
                    result.replyOkIfIdempotent()
                    result.savePendingUids()
                }
        }
}

fun Topology.tpStream(
    utbetalinger: GlobalKTable<String, Utbetaling>,
    saker: KTable<SakKey, Set<UtbetalingId>>,
    kafka: Streams,
) {
    consume(Topics.tp)
        // .repartition(Topics.tp, 3, "from-${Topics.tp.name}")
        // .merge(consume(Topics.tpUtbetalinger))
        .map { key, tp -> TpTuple(key, tp) }
        .rekey { (_, tp) -> SakKey(SakId(tp.sakId), Fagsystem.TILTAKSPENGER) }
        .leftJoin(Serde.json(), Serde.json(), saker, "tptuple-leftjoin-saker")
        .peek { key, _, saker -> kafkaLog.info("joined with saker on key:$key. Uids: $saker") }
        .includeHeader(FS_KEY) { Fagsystem.TILTAKSPENGER.name }
        .map { sakKey, req, ids -> TpDto.splitToDomain(sakKey.sakId, req.key, req.value, ids)  }
        .rekey { dtos -> dtos.first().originalKey }
        .map { value ->
            Result.catch {
                val store = kafka.getStore(Stores.utbetalinger)
                val aggregate = value.map { new ->
                    val prev = store.getOrNull(new.uid.toString())
                    StreamsPair(new, prev)
                }
                val oppdragToUtbetalinger = AggregateService.utledOppdrag(aggregate.filter { (new, _) -> !new.dryrun })
                val simuleringer = AggregateService.utledSimulering(aggregate.filter { (new, _) -> new.dryrun })
                oppdragToUtbetalinger to simuleringer
            }
        }
        .branch(Result<*, *>::isErr, ::replyError)
        .default {
            val result = this.map { it.unwrap() }
            result.saveUtbetalingerAsPending()
            result.sendSimulering()
            result.sendOppdrag()
            result.replyOkIfIdempotent()
            result.savePendingUids()
        }
}

fun Topology.historiskStream(
    utbetalinger: GlobalKTable<String, Utbetaling>,
    saker: KTable<SakKey, Set<UtbetalingId>>,
    kafka: Streams,
) {
    consume(Topics.historisk)
        .repartition(Topics.historisk, 3, "from-${Topics.historisk.name}")
        .merge(consume(Topics.historiskIntern))
        .map { key, historisk -> HistoriskTuple(key, historisk) }
        .rekey { (_, historisk) -> SakKey(SakId(historisk.sakId), Fagsystem.HISTORISK) }
        .leftJoin(Serde.json(), Serde.json(), saker, "historisktuple-leftjoin-saker")
        .peek { key, _, saker -> kafkaLog.info("joined with saker on key:$key. Uids: $saker") }
        .includeHeader(FS_KEY) { Fagsystem.HISTORISK.name }
        .map { req, uids -> HistoriskUtbetaling.toDomain(req.key, req.value, uids)}
        .rekey { dto -> dto.originalKey }
        .map { new ->
            Result.catch {
                val store = kafka.getStore(Stores.utbetalinger)
                val prev = store.getOrNull(new.uid.toString())
                val aggregate = listOf(StreamsPair(new, prev))
                val oppdragToUtbetalinger = AggregateService.utledOppdrag(aggregate.filter { (new, _) -> !new.dryrun })
                val simuleringer = AggregateService.utledSimulering(aggregate.filter { (new, _) -> new.dryrun })
                oppdragToUtbetalinger to simuleringer
            }
        }
        .branch(Result<*, *>::isErr, ::replyError)
        .default {
            val result = this.map { it.unwrap() }
            result.saveUtbetalingerAsPending()
            result.sendSimulering()
            result.sendOppdrag()
            result.replyOkIfIdempotent()
            result.savePendingUids()
        }
}

/**
 * Dagpenger har ikke alltid meldeperioder, 
 * da avbryter vi og svarer med OK med en gang.
 **/
private object Guard {
    // Dagpenger
    fun ifNoMeldeperiode(pair: StreamsPair<DpTuple, Set<UtbetalingId>?>): Boolean {
        val (utbetalinger, saker) = pair.left.value.utbetalinger to pair.right
        return utbetalinger.isEmpty() && saker.isNullOrEmpty()
    }

    fun replyOk(branch: MappedStream<SakKey, StreamsPair<DpTuple, Set<UtbetalingId>?>>) {
        branch.rekey { (dpTuple, _) -> dpTuple.key }
            .map { StatusReply.ok() }
            .produce(Topics.status)
    }

    // Tilleggsstønader
    fun ifNoUtbetalinger(pair: StreamsPair<TsTuple, Set<UtbetalingId>?>): Boolean {
        val (utbetalinger, saker) = pair.left.value.utbetalinger to pair.right
        return utbetalinger.isEmpty() && saker.isNullOrEmpty()
    }

    fun replyOkTs(branch: MappedStream<SakKey, StreamsPair<TsTuple, Set<UtbetalingId>?>>) {
        branch.rekey { (tsTuple, _) -> tsTuple.key }
            .map { StatusReply.ok() }
            .produce(Topics.status)
    }
}

/**
 * Vi må vente med å lagre helved.utbetalinger.v1 til vi har fått en positiv kvittering.
 * Hvis vi ikke klarer å validere med feil og Oppdrag UR svarer med 08 eller 12,
 * så er det fortsatt den forrige utbetalingen som skal gjelde.
 * Vi bruker Oppdrag (request) som kafka-key og må derfor fjerne mmel fra Oppdrag (response) for å trigge en join.
 * Resultatet av joinen kan ikke være null, da har vi en bug.
 */
fun Topology.successfulUtbetalingStream(fks: KTable<Oppdrag, PKs>, pending: KTable<String, Utbetaling>) {
    consume(Topics.oppdrag)
        .filter { it.mmel?.alvorlighetsgrad?.trimEnd() in listOf("00", "04") }
        .rekey { it.apply { it.mmel = null } }
        .map {
            val last = it.oppdrag110.oppdragsLinje150s.last()
            """
            ${it.oppdrag110.kodeFagomraade} 
            sak:${it.oppdrag110.fagsystemId} 
            last.beh:${last.henvisning} 
            last.delytelse:${last.delytelseId}
            """.trimEnd()
        }
        .leftJoin(Serde.xml(), Serde.json(), fks, "oppdrag-leftjoin-fks")
        .flatMapKeyValue { oppdrag, info, pks ->
            if (pks == null) {
                appLog.warn("Fant ikke fks på topic, forsøker å se i datbasen. Oppdraginfo: $info")
                val hashKey = DaoFks.hash(oppdrag)
                val dao = runBlocking {
                    withContext(Jdbc.context) {
                        transaction {
                            DaoFks.firstOrNull(hashKey)
                        }
                    }
                }
                if (dao != null) {
                    dao.uids.map { pk -> KeyValue(pk, info) }
                } else {
                    appLog.warn("primary key used to move pending to utbetalinger was null. Oppdraginfo: $info")
                    emptyList()
                }
            } else {
                pks.uids.map { pk -> KeyValue(pk, info) }
            }
        }
        .leftJoin(Serde.string(), Serde.json(), pending, "pk-leftjoin-pending")
        .mapNotNull { info, pending ->
            if (pending == null) appLog.warn("Fant ikke pending utbetaling. Oppdragsinfo: $info")
            pending
        }
        .produce(Topics.utbetalinger)
}

typealias Aggregate = Pair<List<Pair<Oppdrag, List<Utbetaling>>>, List<SimulerBeregningRequest>>

private fun replyError(branch: MappedStream<String, Result<Aggregate, StatusReply>>) {
    branch
        .map { it.unwrapErr() }
        .produce(Topics.status)
}

private fun MappedStream<String, Aggregate>.replyOkIfIdempotent() {
    this
        .filter { (oppdragToUtbetalinger, simuleringer) -> oppdragToUtbetalinger.isEmpty() && simuleringer.isEmpty() }
        .map { StatusReply.ok() }
        .produce(Topics.status)
}

private fun MappedStream<String, Aggregate>.sendOppdrag() {
    val oppdrag = this.flatMap { (oppdragToUtbetalinger, _) -> oppdragToUtbetalinger.map { it.first } }
    oppdrag.produce(Topics.oppdrag)
    oppdrag.map(StatusReply::mottatt).produce(Topics.status)
}

private fun MappedStream<String, Aggregate>.sendSimulering() {
    this
        .flatMap { (_, simuleringer) -> simuleringer }
        .produce(Topics.simulering)
}

private fun MappedStream<String, Aggregate>.saveUtbetalingerAsPending() {
    this
        .flatMapKeyAndValue { _, (oppdragToUtbetalinger, _) ->
            oppdragToUtbetalinger.flatMap { agg ->
                agg.second.map { KeyValue(it.uid.toString(), it) }
            }
        }
        .produce(Topics.pendingUtbetalinger)
}

private fun MappedStream<String, Aggregate>.savePendingUids() {
    this
        .flatMapKeyAndValue { transactionId, (oppdragToUtbetalinger, _) ->
            oppdragToUtbetalinger.map { (oppdrag, utbetalinger) ->
                val uids = utbetalinger.map { u -> u.uid.toString() }
                val dao = DaoFks(uids)
                runBlocking {
                    withContext(Jdbc.context) {
                        transaction {
                            dao.insert(oppdrag)
                        }
                    }
                }
                KeyValue(oppdrag, PKs(transactionId, uids))
            }
        }
        .produce(Topics.fk)
}

