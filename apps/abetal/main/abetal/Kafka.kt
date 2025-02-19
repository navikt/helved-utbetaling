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
        .rekey { (_, aap) -> "${Fagsystem.from(aap.data.stønad)}-${aap.data.sakId.id}" }
        .leftJoinWith(saker) { JsonSerde.jackson() }
        .map { (uid, aap), sakIdWrapper ->
            when (sakIdWrapper) {
                null -> uid to UtbetalingRequest(aap.action, aap.data.copy(førsteUtbetalingPåSak = true))
                else -> uid to UtbetalingRequest(aap.action, aap.data)
            }
        }
        .rekey { (uid, _) -> uid }
        .map { (_, utbetReq) -> utbetReq }
        .leftJoinWith(utbetalinger, JsonSerde::jackson)
        .map { req, prev ->
            Result.catch {
                req.data.validate(prev)
                val oppdrag = when (req.action) {
                    Action.CREATE -> OppdragService.opprett(req.data)
                    Action.UPDATE -> OppdragService.update(req.data, prev ?: notFound("previous utbetaling"))
                    Action.DELETE -> OppdragService.delete(req.data, prev ?: notFound("previous utbetaling"))
                }
                val lastPeriodeId = PeriodeId.decode(oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId)
                val utbetaling = req.data.copy(lastPeriodeId = lastPeriodeId)
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

