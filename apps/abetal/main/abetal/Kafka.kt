package abetal

import libs.kafka.*
import libs.kafka.processor.DedupProcessor
import libs.kafka.processor.SuppressProcessor
import libs.utils.appLog
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.apache.kafka.streams.kstream.Materialized
import kotlin.time.Duration.Companion.milliseconds

const val AAP_TX_GAP_MS = 50
const val TS_TX_GAP_MS = 50
const val TP_TX_GAP_MS = 250
const val DP_TX_GAP_MS = 250
const val HISTORISK_TX_GAP_MS = 50

const val FS_KEY = "fagsystem"

object Topics {
    val dp = Topic("teamdagpenger.utbetaling.v1", json<DpUtbetaling>())
    val aap = Topic("aap.utbetaling.v1", json<AapUtbetaling>())
    val ts = Topic("tilleggsstonader.utbetaling.v1", json<TsUtbetaling>())
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
    val tsIntern = Topic("helved.utbetalinger-ts.v1", json<TsUtbetaling>())
    val historisk = Topic("historisk.utbetaling.v1", json<HistoriskUtbetaling>())
    val historiskIntern = Topic("helved.utbetalinger-historisk.v1", json<HistoriskUtbetaling>())
}

object Tables {
    val utbetalinger = Table(Topics.utbetalinger, stateStoreName = "${Topics.utbetalinger.name}-state-store-v2")
    val pendingUtbetalinger = Table(Topics.pendingUtbetalinger, stateStoreName = "${Topics.pendingUtbetalinger.name}-state-store-v2")
    val saker = Table(Topics.saker)
    val fk = Table(Topics.fk, stateStoreName = "${Topics.fk.name}-state-store-v2")
}

object Stores {
    val dpAggregate        = Store("dp-aggregate-store-v2",        Serdes(WindowedStringSerde, JsonSerde.listStreamsPair<Utbetaling, Utbetaling?>()))
    val aapAggregate       = Store("aap-aggregate-store-v2",       Serdes(WindowedStringSerde, JsonSerde.listStreamsPair<Utbetaling, Utbetaling?>()))
    val tsAggregate        = Store("ts-aggregate-store-v2",        Serdes(WindowedStringSerde, JsonSerde.listStreamsPair<Utbetaling, Utbetaling?>()))
    val tpAggregate        = Store("tp-aggregate-store-v2",        Serdes(WindowedStringSerde, JsonSerde.listStreamsPair<Utbetaling, Utbetaling?>()))
    val historiskAggregate = Store("historisk-aggregate-store-v2", Serdes(WindowedStringSerde, JsonSerde.listStreamsPair<Utbetaling, Utbetaling?>()))

    val aapDedup           = Store("aap-dedup-aggregate-v2",       jsonListStreamsPair<Utbetaling>())
    val dpDedup            = Store("dp-dedup-aggregate-v2",        jsonListStreamsPair<Utbetaling>())
    val tpDedup            = Store("tp-dedup-aggregate-v2",        jsonListStreamsPair<Utbetaling>())
    val tsDedup            = Store("ts-dedup-aggregate-v2",        jsonListStreamsPair<Utbetaling>())
    val historiskDedup     = Store("historisk-dedup-aggregate-v2", jsonListStreamsPair<Utbetaling>())
}

fun createTopology(): Topology = topology {
    val utbetalinger = consume(Tables.utbetalinger, materializeWithTrace = true)
    val pendingUtbetalinger = consume(Tables.pendingUtbetalinger, materializeWithTrace = true)
    val saker = utbetalingToSak(utbetalinger)
    val fks = consume(Tables.fk, materializeWithTrace = true)
    dpStream(utbetalinger, saker)
    aapStream(utbetalinger, saker)
    tsStream(utbetalinger, saker)
    tpStream(utbetalinger, saker)
    historiskStream(utbetalinger, saker)
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
        .filter {
            it.mmel?.alvorlighetsgrad?.trimEnd() in listOf("00", "04")
            // && it.oppdrag110.kodeFagomraade.trimEnd() in listOf("AAP", "DP")
        }
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
            // requireNotNull(pending) { "Fant ikke pending utbetaling. Oppdragsinfo: $info" }
            pending
        }
        .produce(Topics.utbetalinger)
}

/**
 * Hver gang helved.utbetalinger.v1 blir produsert til
 * akkumulerer vi uids (UtbetalingID) for saken og erstatter aggregatet på helved.saker.v1.
 * Dette gjør at vi kan holde på alle aktive uids for en sakid per fagsystem.
 * Slettede utbetalinger fjernes fra lista.
 * Hvis lista er tom men ikke null betyr det at det ikke er første utbetaling på sak.
 */
fun utbetalingToSak(utbetalinger: KTable<String, Utbetaling>): KTable<SakKey, Set<UtbetalingId>> {
    val ktable = utbetalinger
        .toStream()
        .rekey { _, utbetaling ->
            val fagsystem = if (utbetaling.fagsystem.isTilleggsstønader()) {
                Fagsystem.TILLEGGSSTØNADER
            } else {
                utbetaling.fagsystem
            }
            SakKey(utbetaling.sakId, fagsystem)
        }
        .groupByKey(Serde.json(), Serde.json(), "utbetalinger-groupby-sakkey")
        .aggregate(Tables.saker) { _, utbetaling, uids ->
            when (utbetaling.action) {
                Action.DELETE -> uids - utbetaling.uid
                else -> uids + utbetaling.uid
            }
        }

    ktable
        .toStream()
        .produce(Topics.saker)

    return ktable
}

/**
 * Dagpenger sender en tykk melding med mange meldeperioder.
 * Disse splittes opp i separate utbetalinger og vi utleder en deterministisk uid basert på meldeperioden.
 * For å finne ut om utbetalingene er ny, endret, slettet, må vi joine med saker for å finne alle tidligere utbetalinger.
 * Aggregatet slår sammen utbetalingene / oppdragene og lager èn oppdrag per sak (forventer bare 1 sak om gangen men fler er støttet).
 * Hele oppdraget med alle utbetalingene blir enten OK eller FAILED.
 * Status og oppdrag bruker ikke uid som kafka-key men den orginale keyen som kommer fra Topics.dp.
 * Dette gjør det enklere for konsumentene å korrelere innsendt request med statuser.
 * Til slutt lagrer vi en liste med primary keys på foreign-key topicet som senere brukes
 * til å enten skippe eller persistere utbetalinger (setter de fra pending til aktuell).
 * Vi bruker da Oppdraget (requesten) som kafka-key
 */
fun Topology.dpStream(utbetalinger: KTable<String, Utbetaling>, saker: KTable<SakKey, Set<UtbetalingId>>) {
    val suppress = SuppressProcessor.supplier(Stores.dpAggregate, DP_TX_GAP_MS.milliseconds, DP_TX_GAP_MS.milliseconds)
    val dedup = DedupProcessor.supplier(DP_TX_GAP_MS.milliseconds, Stores.dpDedup, true)

    consume(Topics.dp)
        .repartition(Topics.dp, 3, "from-${Topics.dp.name}")
        .merge(consume(Topics.dpIntern))
        .map { key, dp -> DpTuple(key, dp) }
        .rekey { (_, dp) -> SakKey(SakId(dp.sakId), Fagsystem.DAGPENGER) }
        .leftJoin(Serde.json(), Serde.json(), saker, "dptuple-leftjoin-saker")
        .branch({ (dpTuple, saker) -> dpTuple.value.utbetalinger.isEmpty() && saker.isNullOrEmpty() }) {
            this.rekey { (dpTuple, _) -> dpTuple.key }
                .map { StatusReply.ok() }
                .produce(Topics.status)
        }.default {
            this.flatMapKeyAndValue(::splitOnMeldeperiode)
                .leftJoin(Serde.string(), Serde.json(), utbetalinger, "dp-periode-leftjoin-utbetalinger")
                .rekey { new, _ -> new.originalKey }
                .map { new, prev -> listOf(StreamsPair(new, prev)) }
                .sessionWindow(
                    Serde.string(),
                    Serde.listStreamsPair(),
                    DP_TX_GAP_MS.milliseconds,
                    "dp-utbetalinger-session"
                )
                .reduce(suppress, dedup, Stores.dpAggregate.name) { acc, next -> acc + next }
                .map { aggregate ->
                    Result.catch {
                        val oppdragToUtbetalinger =
                            AggregateService.utledOppdrag(aggregate.filter { (new, _) -> !new.dryrun })
                        val simuleringer =
                            AggregateService.utledSimulering(aggregate.filter { (new, _) -> new.dryrun })
                        oppdragToUtbetalinger to simuleringer
                    }
                }
                .includeHeader(FS_KEY) { Fagsystem.DAGPENGER.name }
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

                    val oppdrag =
                        result.flatMap { (oppdragToUtbetalinger, _) -> oppdragToUtbetalinger.map { it.first } }
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

}

fun Topology.aapStream(utbetalinger: KTable<String, Utbetaling>, saker: KTable<SakKey, Set<UtbetalingId>>) {
    val suppress = SuppressProcessor.supplier(Stores.aapAggregate, AAP_TX_GAP_MS.milliseconds, AAP_TX_GAP_MS.milliseconds)
    val dedup = DedupProcessor.supplier(AAP_TX_GAP_MS.milliseconds, Stores.aapDedup, true)

    consume(Topics.aap)
        .repartition(Topics.aap, 3, "from-${Topics.aap.name}")
        .merge(consume(Topics.aapIntern))
        .map { key, aap -> AapTuple(key, aap) }
        .rekey { (_, aap) -> SakKey(SakId(aap.sakId), Fagsystem.AAP) }
        .leftJoin(Serde.json(), Serde.json(), saker, "aaptuple-leftjoin-saker")
        .flatMapKeyValue(::splitOnMeldeperiode)
        .leftJoin(Serde.string(), Serde.json(), utbetalinger, "aap-periode-leftjoin-utbetalinger")
        .filter { (new, prev) -> !new.isDuplicate(prev) }
        .rekey { new, _ -> new.originalKey }
        .map { new, prev -> listOf(StreamsPair(new, prev)) }
        .sessionWindow(Serde.string(), Serde.listStreamsPair(), AAP_TX_GAP_MS.milliseconds, "aap-utbetalinger-session")
        .reduce(suppress, dedup, Stores.aapAggregate.name) { acc, next -> acc + next }
        .map { aggregate ->
            Result.catch {
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

fun Topology.tsStream(utbetalinger: KTable<String, Utbetaling>, saker: KTable<SakKey, Set<UtbetalingId>>) {
    val suppress = SuppressProcessor.supplier(Stores.tsAggregate, TS_TX_GAP_MS.milliseconds, TS_TX_GAP_MS.milliseconds)
    val dedup = DedupProcessor.supplier(TS_TX_GAP_MS.milliseconds, Stores.tsDedup, true)

    consume(Topics.ts)
        .repartition(Topics.ts, 3, "from-${Topics.ts.name}")
        .merge(consume(Topics.tsIntern))
        .map { key, ts -> TsTuple(key, ts) }
        .rekey { (_, ts) -> SakKey(SakId(ts.sakId), Fagsystem.TILLEGGSSTØNADER) }
        .leftJoin(Serde.json(), Serde.json(), saker, "tstuple-leftjoin-saker")
        .mapKeyValue(::fromTs)
        .leftJoin(Serde.string(), Serde.json(), utbetalinger, "ts-periode-leftjoin-utbetalinger")
        .filter { (new, prev) -> !new.isDuplicate(prev) }
        .rekey { new, _ -> new.originalKey }
        .map { new, prev -> listOf(StreamsPair(new, prev)) }
        .sessionWindow(Serde.string(), Serde.listStreamsPair(), TS_TX_GAP_MS.milliseconds, "ts-utbetalinger-session")
        .reduce(suppress, dedup, Stores.tsAggregate.name) { acc, next -> acc + next }
        .map { aggregate ->
            Result.catch {
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

fun Topology.historiskStream(utbetalinger: KTable<String, Utbetaling>, saker: KTable<SakKey, Set<UtbetalingId>>) {
    val suppress = SuppressProcessor.supplier(Stores.historiskAggregate, HISTORISK_TX_GAP_MS.milliseconds, HISTORISK_TX_GAP_MS.milliseconds)
    val dedup = DedupProcessor.supplier(HISTORISK_TX_GAP_MS.milliseconds, Stores.historiskDedup, true)

    consume(Topics.historisk)
        .repartition(Topics.historisk, 3, "from-${Topics.historisk.name}")
        .merge(consume(Topics.historiskIntern))
        .map { key, historisk -> HistoriskTuple(key, historisk) }
        .rekey { (_, historisk) -> SakKey(SakId(historisk.sakId), Fagsystem.HISTORISK) }
        .leftJoin(Serde.json(), Serde.json(), saker, "historisktuple-leftjoin-saker")
        .mapKeyValue(::fromHistorisk)
        .leftJoin(Serde.string(), Serde.json(), utbetalinger, "historisk-periode-leftjoin-utbetalinger")
        .filter { (new, prev) -> !new.isDuplicate(prev) }
        .rekey { new, _ -> new.originalKey }
        .map { new, prev -> listOf(StreamsPair(new, prev)) }
        .sessionWindow(
            Serde.string(),
            Serde.listStreamsPair(),
            HISTORISK_TX_GAP_MS.milliseconds,
            "historisk-utbetalinger-session"
        )
        .reduce(suppress, dedup, Stores.historiskAggregate.name) { acc, next -> acc + next }
        .map { aggregate ->
            Result.catch {
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

fun Topology.tpStream(utbetalinger: KTable<String, Utbetaling>, saker: KTable<SakKey, Set<UtbetalingId>>) {
    val suppress = SuppressProcessor.supplier(Stores.tpAggregate, TP_TX_GAP_MS.milliseconds, TP_TX_GAP_MS.milliseconds)
    val dedup = DedupProcessor.supplier(TP_TX_GAP_MS.milliseconds, Stores.tpDedup, true)

    consume(Topics.tp)
        // .repartition(Topics.tp, 3, "from-${Topics.tp.name}")
        // .merge(consume(Topics.tpUtbetalinger))
        .map { key, tp -> TpTuple(key, tp) }
        .rekey { (_, tp) -> SakKey(SakId(tp.sakId), Fagsystem.TILTAKSPENGER) }
        .leftJoin(Serde.json(), Serde.json(), saker, "tptuple-leftjoin-saker")
        .flatMapKeyValue(::splitOnMeldeperiode)
        .leftJoin(Serde.string(), Serde.json(), utbetalinger, "tp-periode-leftjoin-utbetalinger")
        .filter { (new, prev) -> !new.isDuplicate(prev) }
        .rekey { new, _ -> new.originalKey }
        .map { new, prev -> listOf(StreamsPair(new, prev)) }
        .sessionWindow(Serde.string(), Serde.listStreamsPair(), TP_TX_GAP_MS.milliseconds, "tp-utbetalinger-session")
        .reduce(suppress, dedup, Stores.tpAggregate.name) { acc, next -> acc + next }
        .map { aggregate ->
            Result.catch {
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
    streamsPair: StreamsPair<DpTuple, Set<UtbetalingId>?>,
): List<KeyValue<String, Utbetaling>> {
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
        val utbetaling = when (tsUtbetaling) {
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
        appLog.info("rekey from $dpKey to ${utbetaling.uid.id} and left join with ${Topics.utbetalinger.name}")
        KeyValue(utbetaling.uid.id.toString(), utbetaling)
    }
}

private fun splitOnMeldeperiode(
    sakKey: SakKey,
    tuple: AapTuple,
    uids: Set<UtbetalingId>?
): List<KeyValue<String, Utbetaling>> {
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
        val utbetaling = when (aapUtbetaling) {
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
        appLog.info("rekey from $aapKey to ${utbetaling.uid.id} and left join with ${Topics.utbetalinger.name}")
        KeyValue(utbetaling.uid.id.toString(), utbetaling)
    }
}

private fun fromTs(sakKey: SakKey, req: TsTuple, uids: Set<UtbetalingId>?): KeyValue<String, Utbetaling> {
    val utbetaling = req.toDomain(uids)
    return KeyValue("${utbetaling.uid}", utbetaling)
}

private fun fromHistorisk(sakKey: SakKey, req: HistoriskTuple, uids: Set<UtbetalingId>?): KeyValue<String, Utbetaling> {
    val utbetaling = req.toDomain(uids)
    return KeyValue("${utbetaling.uid}", utbetaling)
}

private fun splitOnMeldeperiode(
    sakKey: SakKey,
    tuple: TpTuple,
    uids: Set<UtbetalingId>?
): List<KeyValue<String, Utbetaling>> {
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
        .groupBy { it.meldeperiode }
        .map { (meldeperiode, utbetalinger) ->
            tpUId(tpUtbetaling.sakId, meldeperiode, tpUtbetaling.stønad) to tpUtbetaling.copy(perioder = utbetalinger)
        }
        .toMutableList()

    if (uids != null) {
        val tpUids = utbetalingerPerMeldekort.map { (tpUid, _) -> tpUid }
        val missingMeldeperioder = uids.filter { it !in tpUids }.map { it to null }
        utbetalingerPerMeldekort.addAll(missingMeldeperioder)
    }

    return utbetalingerPerMeldekort.map { (uid, tpUtbetaling) ->
        val utbetaling = when (tpUtbetaling) {
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

            else -> tuple.toDomain(uid, uids)
        }
        appLog.debug("rekey to ${utbetaling.uid.id} and left join with ${Topics.utbetalinger.name}")
        KeyValue(utbetaling.uid.id.toString(), utbetaling)
    }
}

