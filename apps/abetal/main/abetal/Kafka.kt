package abetal

import libs.kafka.*
import libs.kafka.processor.SuppressProcessor
import models.*
import libs.utils.appLog
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object Topics {
    val dp = Topic("teamdagpenger.utbetaling.v1", json<DpUtbetaling>())
    val aap = Topic("aap.utbetaling.v1", json<AapUtbetaling>())
    val utbetalinger = Topic("helved.utbetalinger.v1", json<Utbetaling>())
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val simulering = Topic("helved.simuleringer.v1", jaxb<SimulerBeregningRequest>())
    val status = Topic("helved.status.v1", json<StatusReply>())
    val saker = Topic("helved.saker.v1", jsonjsonSet<SakKey, UtbetalingId>())
    val pendingUtbetalinger = Topic("helved.pending-utbetalinger.v1", json<Utbetaling>())
    val fk = Topic("helved.fk.v1", Serdes(XmlSerde.xml<Oppdrag>(), JsonSerde.jackson<PKs>()))
    val dpUtbetalinger = Topic("helved.utbetalinger-dp.v1", json<DpUtbetaling>())
    val aapUtbetalinger = Topic("helved.utbetalinger-aap.v1", json<AapUtbetaling>())
}

object Tables {
    val utbetalinger = Table(Topics.utbetalinger)
    val pendingUtbetalinger = Table(Topics.pendingUtbetalinger)
    val saker = Table(Topics.saker)
    val fk = Table(Topics.fk)
}

object Stores {
    val utbetalinger = Store(Tables.utbetalinger)
    val dpAggregate = Store("dp-aggregate-store", Serdes(WindowedStringSerde, JsonSerde.listStreamsPair<Utbetaling, Utbetaling?>()))
    val aapAggregate = Store("aap-aggregate-store", Serdes(WindowedStringSerde, JsonSerde.listStreamsPair<Utbetaling, Utbetaling?>()))
}

fun createTopology(): Topology = topology {
    val utbetalinger = consume(Tables.utbetalinger)
    val pendingUtbetalinger = consume(Tables.pendingUtbetalinger)
    val saker = utbetalingToSak(utbetalinger)
    val fks = consume(Tables.fk)
    dpStream(utbetalinger, saker)
    aapStream(utbetalinger, saker)
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
        .filter { it.mmel?.alvorlighetsgrad?.trimEnd() in listOf("00", "04") && it.oppdrag110.kodeFagomraade.trimEnd() in listOf("AAP", "DP") }
        .rekey { it.apply { it.mmel = null} }
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
        .flatMapKeyValue { o, info, pks -> 
            if (pks == null) {
                appLog.warn("primary key used to move pending to utbetalinger was null. Oppdraginfo: $info")
                emptyList()
            } else {
                pks.uids.map { pk -> KeyValue(pk, info)}
            }
        }
        .leftJoin(Serde.string(), Serde.json(), pending, "pk-leftjoin-pending")
        .map { info, pending -> requireNotNull(pending) { "Fant ikke pending utbetaling. Oppdragsinfo: $info" } }
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
        .rekey { _, utbetaling -> SakKey(utbetaling.sakId, Fagsystem.from(utbetaling.stønad)) }
        .groupByKey(Serde.json(), Serde.json(), "utbetalinger-groupby-sakkey")
        .aggregate(Tables.saker) { _, utbetaling, uids -> 
            when(utbetaling.action) {
                Action.DELETE -> uids - utbetaling.uid
                else -> uids + utbetaling.uid
            }
        } 

    ktable
        .toStream()
        .produce(Topics.saker)

    return ktable
}

private val dpSuppressProcessorSupplier = SuppressProcessor.supplier(Stores.dpAggregate, 100.milliseconds, 1.seconds)
private val aapSuppressProcessorSupplier = SuppressProcessor.supplier(Stores.aapAggregate, 100.milliseconds, 1.seconds)


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
    consume(Topics.dp)
        .repartition(Topics.dp, 3, "from-${Topics.dp.name}")
        .merge(consume(Topics.dpUtbetalinger)) // vi i team helved må kunne teste samme løype som dp
        .map { key, dp -> DpTuple(key, dp) }
        .rekey { (_, dp) -> SakKey(SakId(dp.sakId), Fagsystem.DAGPENGER) }
        .leftJoin(Serde.json(), Serde.json(), saker, "dptuple-leftjoin-saker")
        .flatMapKeyValue(::splitOnMeldeperiode)
        .leftJoin(Serde.string(), Serde.json(), utbetalinger, "dp-periode-leftjoin-utbetalinger")
        .filter { (new, prev) -> !new.isDuplicate(prev) }
        .rekey { new, _ -> new.originalKey }
        .map { new, prev -> listOf(StreamsPair(new, prev)) }
        .sessionWindow(Serde.string(), Serde.listStreamsPair(), 1.seconds, "dp-utbetalinger-session") 
        .reduce(dpSuppressProcessorSupplier, Stores.dpAggregate.name)  { acc, next -> acc + next }
        .map { aggregate  -> 
            Result.catch { 
                val oppdragToUtbetalinger = AggregateService.utledOppdrag(aggregate.filter { (new, _) -> !new.dryrun })
                val simuleringer = AggregateService.utledSimulering(aggregate.filter { (new, _) -> new.dryrun })
                oppdragToUtbetalinger to simuleringer
            }
        }
        .branch({ it.isOk() }) {
            val result = map { it -> it.unwrap() }

            result
                .flatMapKeyAndValue { _, (oppdragToUtbetalinger, _) -> oppdragToUtbetalinger.flatMap { agg -> agg.second.map { KeyValue(it.uid.toString(), it) } } }
                .produce(Topics.pendingUtbetalinger)

            result
                .flatMap { (_, simuleringer) -> simuleringer } 
                .produce(Topics.simulering)

            val oppdrag = result.flatMap { (oppdragToUtbetalinger, _) -> oppdragToUtbetalinger.map { it.first } }
            oppdrag.produce(Topics.oppdrag)
            oppdrag.map { StatusReply.mottatt(it) }.produce(Topics.status)

            result
                .flatMapKeyAndValue { originalKey, (oppdragToUtbetalinger, _) -> 
                    oppdragToUtbetalinger.map { (oppdrag, utbetalinger) -> 
                        val uids = utbetalinger.map { u -> u.uid.toString() }
                        KeyValue(oppdrag, PKs(originalKey, uids)) 
                    } 
                }
                .produce(Topics.fk)
        }
        .default {
            map { it -> it.unwrapErr() }.produce(Topics.status) 
        }
}

fun Topology.aapStream(utbetalinger: KTable<String, Utbetaling>, saker: KTable<SakKey, Set<UtbetalingId>>) {
    consume(Topics.aap)
        .repartition(Topics.aap, 3, "from-${Topics.aap.name}")
        .merge(consume(Topics.aapUtbetalinger)) // vi i team helved må kunne teste samme løype som aap
        .map { key, aap -> AapTuple(key, aap) }
        .rekey { (_, aap) -> SakKey(SakId(aap.sakId), Fagsystem.AAP) }
        .leftJoin(Serde.json(), Serde.json(), saker, "aaptuple-leftjoin-saker")
        .flatMapKeyValue(::splitOnMeldeperiode)
        .leftJoin(Serde.string(), Serde.json(), utbetalinger, "aap-periode-leftjoin-utbetalinger")
        .filter { (new, prev) -> !new.isDuplicate(prev) }
        .rekey { new, _ -> new.originalKey }
        .map { new, prev -> listOf(StreamsPair(new, prev)) }
        .sessionWindow(Serde.string(), Serde.listStreamsPair(), 1.seconds, "aap-utbetalinger-session")
        .reduce(aapSuppressProcessorSupplier, Stores.aapAggregate.name)  { acc, next -> acc + next }
        .map { aggregate  ->
            Result.catch {
                val oppdragToUtbetalinger = AggregateService.utledOppdrag(aggregate.filter { (new, _) -> !new.dryrun })
                val simuleringer = AggregateService.utledSimulering(aggregate.filter { (new, _) -> new.dryrun })
                oppdragToUtbetalinger to simuleringer
            }
        }
        .branch({ it.isOk() }) {
            val result = map { it -> it.unwrap() }

            result
                .flatMapKeyAndValue { _, (oppdragToUtbetalinger, _) -> oppdragToUtbetalinger.flatMap { agg -> agg.second.map { KeyValue(it.uid.toString(), it) } } }
                .produce(Topics.pendingUtbetalinger)

            result
                .flatMap { (_, simuleringer) -> simuleringer }
                .produce(Topics.simulering)

            val oppdrag = result.flatMap { (oppdragToUtbetalinger, _) -> oppdragToUtbetalinger.map { it.first } }
            oppdrag.produce(Topics.oppdrag)
            oppdrag.map { StatusReply.mottatt(it) }.produce(Topics.status)

            result
                .flatMapKeyAndValue { originalKey, (oppdragToUtbetalinger, _) ->
                    oppdragToUtbetalinger.map { (oppdrag, utbetalinger) ->
                        val uids = utbetalinger.map { u -> u.uid.toString() }
                        KeyValue(oppdrag, PKs(originalKey, uids))
                    }
                }
                .produce(Topics.fk)
        }
        .default {
            map { it -> it.unwrapErr() }.produce(Topics.status)
        }
}
// TODO: erstatt DpTuple med noe mer generisk
private fun splitOnMeldeperiode(sakKey: SakKey, tuple: DpTuple, uids: Set<UtbetalingId>?): List<KeyValue<String, Utbetaling>> {
    val (dpKey, dpUtbetaling) = tuple
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

    return utbetalingerPerMeldekort.map { (uid, dpUtbetaling) ->
        val utbetaling = when (dpUtbetaling) {
            null -> fakeDelete(dpKey, sakKey.sakId, uid, Fagsystem.DAGPENGER, StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
                Navident("dagpenger"), Navident("dagpenger")).also { appLog.debug("creating a fake delete to force-trigger a join with existing utbetaling") }
            else -> toDomain(dpKey, dpUtbetaling, uids, uid)
        }
        appLog.debug("rekey to ${utbetaling.uid.id} and left join with ${Topics.utbetalinger.name}")
        KeyValue(utbetaling.uid.id.toString(), utbetaling)
    }
}

// TODO: erstatt DpTuple med noe mer generisk
private fun splitOnMeldeperiode(sakKey: SakKey, tuple: AapTuple, uids: Set<UtbetalingId>?): List<KeyValue<String, Utbetaling>> {
    val (aapKey, aapUtbetaling) = tuple
    val utbetalingerPerMeldekort: MutableList<Pair<UtbetalingId, AapUtbetaling?>> = aapUtbetaling
        .utbetalinger
        .groupBy { it.meldeperiode }
        .map { (meldeperiode, utbetalinger) ->
            aapUId(aapUtbetaling.sakId, meldeperiode, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING) to aapUtbetaling.copy(utbetalinger = utbetalinger)
        }
        .toMutableList()

    if (uids != null) {
        val aapUids = utbetalingerPerMeldekort.map { (aapUid, _) -> aapUid }
        val missingMeldeperioder = uids.filter { it !in aapUids }.map { it to null }
        utbetalingerPerMeldekort.addAll(missingMeldeperioder)
    }

    return utbetalingerPerMeldekort.map { (uid, aapUtbetaling) ->
        val utbetaling = when (aapUtbetaling) {
            null -> fakeDelete(aapKey, sakKey.sakId, uid, Fagsystem.AAP, StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                Navident("kelvin"), Navident("kelvin")).also { appLog.debug("creating a fake delete to force-trigger a join with existing utbetaling") }
            else -> toDomain(aapKey, aapUtbetaling, uids, uid)
        }
        appLog.debug("rekey to ${utbetaling.uid.id} and left join with ${Topics.utbetalinger.name}")
        KeyValue(utbetaling.uid.id.toString(), utbetaling)
    }
}