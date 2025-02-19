package abetal

import abetal.models.*
import libs.kafka.*
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import java.util.UUID

object Topics {
    val aap = Topic<AapUtbetaling>("aap.utbetalinger.v1", JsonSerde.jackson())
    val utbetalinger = Topic<Utbetaling>("helved.utbetalinger.v1", JsonSerde.jackson())
    val oppdrag = Topic<Oppdrag>("helved.oppdrag.v1", XmlSerde.serde())
    val status = Topic<StatusReply>("helved.status.v1", JsonSerde.jackson())
    val saker = Topic<SakIdWrapper>("helved.saker.v1", JsonSerde.jackson())
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

fun utbetalingToSak(utbetalinger: KTable<Utbetaling>, saker: KTable<SakIdWrapper>) {
    utbetalinger
        .toStream()
        .map { key, utbetaling -> UtbetalingTuple(UUID.fromString(key), utbetaling) }
        .rekey { (_, utbetaling) -> "${Fagsystem.from(utbetaling.stønad)}-${utbetaling.sakId.id}" }
        .leftJoinWith(saker) { JsonSerde.jackson() }
        .map { (uid, utbetaling), sakIdWrapper ->
            when (sakIdWrapper) {
                null -> SakIdWrapper(utbetaling.sakId.id, setOf(UtbetalingId(uid)))
                else -> SakIdWrapper(utbetaling.sakId.id, sakIdWrapper.uids + UtbetalingId(uid))
            }
        }
        .produce(Topics.saker)
}

data class AapTuple(
    val uid: String,
    val aap: AapUtbetaling,
)

fun Topology.aapStream(utbetalinger: KTable<Utbetaling>, saker: KTable<SakIdWrapper>) {
    consume(Topics.aap)
        .map { key, aap -> AapTuple(key, aap) }
        .rekey { (_, aap) -> "${Fagsystem.from(aap.stønad)}-${aap.sakId.id}" }
        .leftJoinWith(saker) { JsonSerde.jackson() }
        .map(::toDomain)
        .rekey { utbetaling -> utbetaling.uid.id.toString() }
        .leftJoinWith(utbetalinger, JsonSerde::jackson)
        .map { new, prev ->
            Result.catch {
                new.validate(prev)
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

