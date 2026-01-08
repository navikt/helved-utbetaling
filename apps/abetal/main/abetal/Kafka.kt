package abetal

import libs.kafka.*
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
    dagpengerStream(utbetalinger, saker, kafka)
    aapStream(utbetalinger, saker, kafka)
    tsStream(utbetalinger, saker, kafka)
    tpStream(utbetalinger, saker, kafka)
    historiskStream(utbetalinger, saker, kafka)
    successfulUtbetalingStream(fks, pendingUtbetalinger)
}

data class SakKey(val sakId: SakId, val fagsystem: Fagsystem)
data class PKs(val originalKey: String, val uids: List<String>)

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
        .flatMapKeyValue { _, info, pks ->
            if (pks == null) {
                appLog.warn("primary key used to move pending to utbetalinger was null. Oppdraginfo: $info")
                emptyList()
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
        .peek { key, _, saker ->
            val bytes = key.toString().toByteArray()
            kafkaLog.info("joined with saker on key:$key bytes: ${bytes.joinToString(","){ it.toString() } } length: ${bytes.size}. Uids: $saker")
        }
        .map(::splitOnMeldeperiode) // rename toDomain
        .rekey { _, dtos -> dtos.first().originalKey } // alle har samme uid, skal ikke være 0 pga branch over
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
        .includeHeader(FS_KEY) { Fagsystem.AAP.name }
        .branch({ it.isOk() }) {
            val result = map { it -> it.unwrap() }

            result
                .flatMapKeyAndValue { _, (oppdragToUtbetalinger, _) ->
                    oppdragToUtbetalinger.flatMap { agg ->
                        agg.second.map {
                            KeyValue(it.uid.toString(), it)
                        }
                    }
                }
                .produce(Topics.pendingUtbetalinger)

            result
                .flatMap { (_, simuleringer) -> simuleringer }
                .produce(Topics.simulering)

            val oppdrag = result.flatMap { (oppdragToUtbetalinger, _) -> oppdragToUtbetalinger.map { it.first } }
            oppdrag.produce(Topics.oppdrag)
            oppdrag
                .map { StatusReply.mottatt(it) }
                .produce(Topics.status)

            result
                .filter { (oppdragToUtbetalinger, simuleringer) -> oppdragToUtbetalinger.isEmpty() && simuleringer.isEmpty() }
                .map { StatusReply.ok() }
                .produce(Topics.status)

            result
                .flatMapKeyAndValue { transactionId, (oppdragToUtbetalinger, _) ->
                    oppdragToUtbetalinger.map { (oppdrag, utbetalinger) ->
                        val uids = utbetalinger.map { u -> u.uid.toString() }
                        KeyValue(oppdrag, PKs(transactionId, uids))
                    }
                }
                .produce(Topics.fk)
        }
        .default {
            map { it -> it.unwrapErr() }.produce(Topics.status)
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
        .map { ts, uids -> ts.toDomain(uids)}
        .rekey { dtos -> dtos.first().originalKey } // alle har samme uid, skal ikke være 0 pga branch over
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
        .includeHeader(FS_KEY) { Fagsystem.TILLEGGSSTØNADER.name }
        .branch({ it.isOk() }) {
            val result = map { it -> it.unwrap() }

            result
                .flatMapKeyAndValue { _, (oppdragToUtbetalinger, _) ->
                    oppdragToUtbetalinger.flatMap { agg ->
                        agg.second.map {
                            KeyValue(it.uid.toString(), it)
                        }
                    }
                }
                .produce(Topics.pendingUtbetalinger)

            result
                .flatMap { (_, simuleringer) -> simuleringer }
                .produce(Topics.simulering)

            val oppdrag = result.flatMap { (oppdragToUtbetalinger, _) -> oppdragToUtbetalinger.map { it.first } }
            oppdrag.produce(Topics.oppdrag)
            oppdrag
                .map { StatusReply.mottatt(it) }
                .produce(Topics.status)

            result
                .filter { (oppdragToUtbetalinger, simuleringer) -> oppdragToUtbetalinger.isEmpty() && simuleringer.isEmpty() }
                .map { StatusReply.ok() }
                .produce(Topics.status)

            result
                .flatMapKeyAndValue { transactionId, (oppdragToUtbetalinger, _) ->
                    oppdragToUtbetalinger.map { (oppdrag, utbetalinger) ->
                        val uids = utbetalinger.map { u -> u.uid.toString() }
                        KeyValue(oppdrag, PKs(transactionId, uids))
                    }
                }
                .produce(Topics.fk)
        }
        .default {
            map { it -> it.unwrapErr() }.produce(Topics.status)
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
        .map { ts, uids -> ts.toDomain(uids)}
        .rekey { dto -> dto.originalKey } // alle har samme uid, skal ikke være 0 pga branch over
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
        .includeHeader(FS_KEY) { Fagsystem.HISTORISK.name }
        .branch({ it.isOk() }) {
            val result = map { it -> it.unwrap() }

            result
                .flatMapKeyAndValue { _, (oppdragToUtbetalinger, _) ->
                    oppdragToUtbetalinger.flatMap { agg ->
                        agg.second.map {
                            KeyValue(
                                it.uid.toString(),
                                it
                            )
                        }
                    }
                }
                .produce(Topics.pendingUtbetalinger)

            result
                .flatMap { (_, simuleringer) -> simuleringer }
                .produce(Topics.simulering)

            val oppdrag = result.flatMap { (oppdragToUtbetalinger, _) -> oppdragToUtbetalinger.map { it.first } }
            oppdrag.produce(Topics.oppdrag)
            oppdrag.map { StatusReply.mottatt(it) }.produce(Topics.status)

            result
                .filter { (oppdragToUtbetalinger, simuleringer) -> oppdragToUtbetalinger.isEmpty() && simuleringer.isEmpty() }
                .map { StatusReply.ok() }
                .produce(Topics.status)

            result
                .flatMapKeyAndValue { transactionId, (oppdragToUtbetalinger, _) ->
                    oppdragToUtbetalinger.map { (oppdrag, utbetalinger) ->
                        val uids = utbetalinger.map { u -> u.uid.toString() }
                        KeyValue(oppdrag, PKs(transactionId, uids))
                    }
                }
                .produce(Topics.fk)
        }
        .default {
            map { it -> it.unwrapErr() }.produce(Topics.status)
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
        .map { key, tuple, ids -> splitOnTpMeldeperiode(key, tuple, ids)  }
        .rekey { dtos -> dtos.first().originalKey } // alle har samme uid, skal ikke være 0 pga branch over
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
        .includeHeader(FS_KEY) { Fagsystem.TILTAKSPENGER.name }
        .branch({ it.isOk() }) {
            val result = map { it -> it.unwrap() }

            result
                .flatMapKeyAndValue { _, (oppdragToUtbetalinger, _) ->
                    oppdragToUtbetalinger.flatMap { agg ->
                        agg.second.map {
                            KeyValue(
                                it.uid.toString(),
                                it
                            )
                        }
                    }
                }
                .produce(Topics.pendingUtbetalinger)

            result
                .flatMap { (_, simuleringer) -> simuleringer }
                .produce(Topics.simulering)

            val oppdrag = result.flatMap { (oppdragToUtbetalinger, _) -> oppdragToUtbetalinger.map { it.first } }
            oppdrag.produce(Topics.oppdrag)
            oppdrag
                .map { StatusReply.mottatt(it) }
                .produce(Topics.status)

            result
                .filter { (oppdragToUtbetalinger, simuleringer) -> oppdragToUtbetalinger.isEmpty() && simuleringer.isEmpty() }
                .map { StatusReply.ok() }
                .produce(Topics.status)

            result
                .flatMapKeyAndValue { transactionId, (oppdragToUtbetalinger, _) ->
                    oppdragToUtbetalinger.map { (oppdrag, utbetalinger) ->
                        val uids = utbetalinger.map { u -> u.uid.toString() }
                        KeyValue(oppdrag, PKs(transactionId, uids))
                    }
                }
                .produce(Topics.fk)
        }
        .default {
            map { it -> it.unwrapErr() }.produce(Topics.status)
        }
}

private fun splitOnMeldeperiode(
    sakKey: SakKey,
    tuple: AapTuple,
    uids: Set<UtbetalingId>?
): List<Utbetaling> {
    val (aapKey, aapUtbetaling) = tuple
    val dryrun = aapUtbetaling.dryrun
    val personident = aapUtbetaling.ident
    val behandlingId = aapUtbetaling.behandlingId
    val periodetype = Periodetype.UKEDAG
    val vedtakstidspunktet = aapUtbetaling.vedtakstidspunktet
    val beslutterId = aapUtbetaling.beslutter ?: "kelvin"
    val saksbehandler = aapUtbetaling.saksbehandler ?: "kelvin"
    val utbetalingerPerMeldekort: MutableList<Pair<UtbetalingId, AapUtbetaling?>> = aapUtbetaling
        .utbetalinger
        .groupBy { it.meldeperiode }
        .map { (meldeperiode, utbetalinger) ->
            aapUId(aapUtbetaling.sakId, meldeperiode, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING) to aapUtbetaling.copy(
                utbetalinger = utbetalinger
            )
        }
        .toMutableList()

    if (uids != null) {
        val aapUids = utbetalingerPerMeldekort.map { (aapUid, _) -> aapUid }
        val missingMeldeperioder = uids.filter { it !in aapUids }.map { it to null }
        utbetalingerPerMeldekort.addAll(missingMeldeperioder)
    }

    return utbetalingerPerMeldekort.map { (uid, aapUtbetaling) ->
        when (aapUtbetaling) {
            null -> fakeDelete(
                dryrun = dryrun,
                originalKey = aapKey,
                sakId = sakKey.sakId,
                uid = uid,
                fagsystem = Fagsystem.AAP,
                stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                beslutterId = Navident(beslutterId),
                saksbehandlerId = Navident(saksbehandler),
                personident = Personident(personident),
                behandlingId = BehandlingId(behandlingId),
                periodetype = periodetype,
                vedtakstidspunkt = vedtakstidspunktet,
            ).also { appLog.debug("creating a fake delete to " + "force-trigger a join with existing utbetaling") }

            else -> toDomain(aapKey, aapUtbetaling, uids, uid)
        }
    }
}

private fun splitOnTpMeldeperiode(
    sakKey: SakKey,
    tuple: TpTuple,
    uids: Set<UtbetalingId>?,
): List<Utbetaling> {
    val (tpKey, tpUtbetaling) = tuple
    val dryrun = tpUtbetaling.dryrun
    val personident = tpUtbetaling.personident
    val behandlingId = tpUtbetaling.behandlingId
    val periodetype = Periodetype.UKEDAG
    val vedtakstidspunktet = tpUtbetaling.vedtakstidspunkt
    val beslutterId = tpUtbetaling.beslutter ?: "tp"
    val saksbehandler = tpUtbetaling.saksbehandler ?: "tp"
    val utbetalingerPerMeldekort: MutableList<Pair<UtbetalingId, TpUtbetaling?>> = tpUtbetaling
        .perioder
        .groupBy { it.meldeperiode to it.stønad }
        .map { (group, utbetalinger) ->
            val (meldeperiode, stønad) = group
            tpUId(tpUtbetaling.sakId, meldeperiode, stønad) to tpUtbetaling.copy(perioder = utbetalinger)
        }
        .toMutableList()

    if (uids != null) {
        val tpUids = utbetalingerPerMeldekort.map { (tpUid, _) -> tpUid }
        val missingMeldeperioder = uids.filter { it !in tpUids }.map { it to null }
        utbetalingerPerMeldekort.addAll(missingMeldeperioder)
    }

    return utbetalingerPerMeldekort.map { (uid, tpUtbetaling) ->
        when (tpUtbetaling) {
            null -> fakeDelete(
                dryrun = dryrun,
                originalKey = tpKey,
                sakId = sakKey.sakId,
                uid = uid,
                fagsystem = Fagsystem.TILTAKSPENGER,
                StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING, // Dette er en placeholder
                beslutterId = Navident(beslutterId),
                saksbehandlerId = Navident(saksbehandler),
                personident = Personident(personident),
                behandlingId = BehandlingId(behandlingId),
                periodetype = periodetype,
                vedtakstidspunkt = vedtakstidspunktet,
            ).also { appLog.debug("creating a fake delete to force-trigger a join with existing utbetaling") }

            else -> toDomain(tpKey, tpUtbetaling, uids, uid)
        }
    }
}

