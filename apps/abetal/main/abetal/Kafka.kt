package abetal

import abetal.models.*
import abetal.Result
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
}

data class Tuple(
    val uid: String,
    val aap: AapUtbetaling,
)

fun Topology.aapStream(utbetalinger: KTable<Utbetaling>, saker: KTable<SakIdWrapper>) {
    consume(Topics.aap)
        .map { key, aap -> Tuple(key, aap)  }
        .rekey { (_, aap) -> "aap-${aap.data.sakId.id}" }
        .leftJoinWith(saker) { JsonSerde.jackson() }
        .map { (uid, aap), uids ->
            when (uids) {
                null -> uid to UtbetalingRequest(aap.action, aap.data.copy(førsteUtbetalingPåSak = true))
                else -> uid to UtbetalingRequest(aap.action, aap.data)
            }
        }
        .rekey { (uid, _) -> uid }
        .map { (_, utbetReq) -> utbetReq }
        .leftJoinWith(utbetalinger, JsonSerde::jackson)
        .map { req, prev ->
            Result.catch<Pair<Utbetaling, Oppdrag>> {
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
            val result =  this.map { it -> it.unwrap() }
            result.map { (utbetaling, _) -> utbetaling }.produce(Topics.utbetalinger)
            result.map { (_, oppdrag) -> oppdrag }.produce(Topics.oppdrag)
            result.map { (_, _) -> StatusReply() }.produce(Topics.status)
        }.default {
            map { it -> it.unwrapErr() }.produce(Topics.status)
        }
} 

