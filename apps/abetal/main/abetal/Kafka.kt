package abetal

import abetal.models.*
import java.util.UUID
import libs.kafka.*
import libs.kafka.stream.MappedStream
import libs.utils.appLog
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.trygdeetaten.skjema.oppdrag.Oppdrag

object Topics {
    val aap = Topic("helved.utbetalinger-aap.v1", json<AapUtbetaling>())
    val dp = Topic("helved.utbetalinger-dp.v1", json<DpUtbetaling>())
    val utbetalinger = Topic("helved.utbetalinger.v1", json<Utbetaling>())
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val simulering = Topic("helved.simuleringer.v1", jaxb<SimulerBeregningRequest>())
    val status = Topic("helved.status.v1", json<StatusReply>())
    val saker = Topic("helved.saker.v1", jsonjson<SakKey, SakValue>())
}

object Tables {
    val utbetalinger = Table(Topics.utbetalinger)
    val saker = Table(Topics.saker)
}

object Stores {
    val utbetalinger = Store(Tables.utbetalinger)
}

fun createTopology(): Topology = topology {
    val utbetalinger = consume(Tables.utbetalinger)
    val saker = consume(Tables.saker)
    aapStream(utbetalinger, saker)
    dpStream(utbetalinger, saker)
    utbetalingToSak(utbetalinger, saker)
}

data class UtbetalingTuple(val uid: UUID, val utbetaling: Utbetaling)
data class SakKey(val sakId: SakId, val fagsystem: Fagsystem)
data class SakValue(val uids: Set<UtbetalingId>)

fun utbetalingToSak(utbetalinger: KTable<String, Utbetaling>, saker: KTable<SakKey, SakValue>) {
    utbetalinger
        .toStream()
        .map { key, utbetaling -> UtbetalingTuple(UUID.fromString(key), utbetaling) }
        .rekey { (_, utbetaling) -> SakKey(utbetaling.sakId, Fagsystem.from(utbetaling.stønad)) }
        .leftJoin(jsonjson(), saker)
        .map { (uid, _), prevSak -> when (prevSak) {
                null -> SakValue(setOf(UtbetalingId(uid)))
                else -> SakValue(prevSak.uids + UtbetalingId(uid))
            }
        }
        .produce(Topics.saker)
}

fun Topology.aapStream(utbetalinger: KTable<String, Utbetaling>, saker: KTable<SakKey, SakValue>) {
    consume(Topics.aap)
        .repartition(Topics.aap.serdes, 3)
        .map { key, aap -> AapTuple(key, aap) }
        .rekey { (_, aap) -> SakKey(aap.sakId, Fagsystem.from(aap.stønad)) }
        .leftJoin(jsonjson(), saker)
        .map(::toDomain)
        .rekey { utbetaling -> utbetaling.uid.id.toString() }
        .leftJoin(json(), utbetalinger)
        .branch({ (new, _) -> new.dryrun }, ::dryrunStream)
        .default(::oppdragStream)
}

fun Topology.dpStream(utbetalinger: KTable<String, Utbetaling>, saker: KTable<SakKey, SakValue>) {
    consume(Topics.dp)
        .repartition(Topics.dp.serdes, 3)
        .map { key, dp -> DpTuple(key, dp) }
        .rekey { (_, dp) -> SakKey(SakId(dp.fagsakId), Fagsystem.from(dp.stønad)) }
        .leftJoin(jsonjson(), saker)
        .map(::toDomain)
        .rekey { utbetaling -> utbetaling.uid.id.toString() }
        .leftJoin(json(), utbetalinger)
        .branch({ (new, _) -> new.dryrun }, ::dryrunStream)
        .default(::oppdragStream)
}

fun oppdragStream(branched: MappedStream<String, StreamsPair<Utbetaling, Utbetaling?>>) {
    branched.filter { (new, prev) -> 
        val distinct = !new.isDuplicate(prev)
        if (!distinct) appLog.info("Duplicate message found for ${new.uid.id}")
        distinct
    }.map { (new, prev) ->
        Result.catch {
            new.validate(prev)
            val new = new.copy(perioder = new.perioder.aggreger(new.periodetype))
            val oppdrag = when (new.action) {
                Action.CREATE -> OppdragService.opprett(new)
                Action.UPDATE -> OppdragService.update(new, prev ?: notFound("previous utbetaling"))
                Action.DELETE -> OppdragService.delete(new, prev ?: notFound("previous utbetaling"))
            }
            val lastPeriodeId = PeriodeId.decode(oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId)
            val utbetaling = new.copy(lastPeriodeId = lastPeriodeId)
            utbetaling to oppdrag
        }
    }.branch({ it.isOk() }) {
        val result = this.map { it -> it.unwrap() }
        result.map { (utbetaling, _) -> utbetaling }.produce(Topics.utbetalinger)
        result.map { (_, oppdrag) -> oppdrag }.produce(Topics.oppdrag)
        result.map { (_, _) -> StatusReply(Status.MOTTATT) }.produce(Topics.status)
    }.default {
        map { it -> it.unwrapErr() }.produce(Topics.status)
    }
}

fun dryrunStream(branched: MappedStream<String, StreamsPair<Utbetaling, Utbetaling?>>) {
    branched.map { (new, prev) ->
        Result.catch {
            new.validate(prev)
            val new = new.copy(perioder = new.perioder.aggreger(new.periodetype))
            when (new.action) {
                Action.CREATE -> SimuleringService.opprett(new)
                Action.UPDATE -> SimuleringService.update(new, prev ?: notFound("previous utbetaling"))
                Action.DELETE -> SimuleringService.delete(new, prev ?: notFound("previous utbetaling"))
            }
        }
    }.branch( { it.isOk() }) {
        val result = this.map { it -> it.unwrap() }
        result.map { _ -> StatusReply(Status.MOTTATT) }.produce(Topics.status)
        result.produce(Topics.simulering)
    }.default {
        map { it -> it.unwrapErr() }.produce(Topics.status)
    }
}

inline fun <reified K: Any, reified V: Any> jsonjson() = Serdes(JsonSerde.jackson<K>(), JsonSerde.jackson<V>())
