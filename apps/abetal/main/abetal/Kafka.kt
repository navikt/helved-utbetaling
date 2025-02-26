package abetal

import abetal.models.*
import models.*
import libs.kafka.*
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import java.util.UUID

object Topics {
    val aap = Topic("aap.utbetalinger.v1", json<AapUtbetaling>())
    val utbetalinger = Topic("helved.utbetalinger.v1", json<Utbetaling>())
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val status = Topic("helved.status.v1", json<StatusReply>())
    val saker = Topic("helved.saker.v1", jsonjson<SakKey, SakValue>())
}

object Tables {
    val utbetalinger = Table(Topics.utbetalinger)
    val status = Table(Topics.status)
    val saker = Table(Topics.saker)
}

fun createTopology(): Topology = topology {
    val utbetalinger = consume(Tables.utbetalinger)
    val saker = consume(Tables.saker)
    aapStream(utbetalinger, saker)
    utbetalingToSak(utbetalinger, saker)
}

data class UtbetalingTuple(
    val uid: UUID,
    val utbetaling: Utbetaling,
)

fun utbetalingToSak(utbetalinger: KTable<String, Utbetaling>, saker: KTable<SakKey, SakValue>) {
    utbetalinger
        .toStream()
        // .secureLogWithKey { k, v -> warn("k:$k vedtatt:${v.vedtakstidspunkt}") }
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

data class AapTuple(
    val uid: String,
    val aap: AapUtbetaling,
)


fun Topology.aapStream(utbetalinger: KTable<String, Utbetaling>, saker: KTable<SakKey, SakValue>) {
    consume(Topics.aap)
        .map { key, aap -> AapTuple(key, aap) }
        .rekey { (_, aap) -> SakKey(aap.sakId, Fagsystem.from(aap.stønad)) }
        .leftJoin(jsonjson(), saker)
        .map(::toDomain)
        .rekey { utbetaling -> utbetaling.uid.id.toString() }
        .leftJoin(json(), utbetalinger)
        .map { new, prev ->
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
            result.map { (_, _) -> StatusReply() }.produce(Topics.status)
        }.default {
            map { it -> it.unwrapErr() }.produce(Topics.status)
        }
} 

inline fun <reified K: Any, reified V: Any> jsonjson() = Serdes(JsonSerde.jackson<K>(), JsonSerde.jackson<V>())

