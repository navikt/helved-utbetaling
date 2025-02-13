package abetal

import abetal.models.*
import abetal.Result
import libs.kafka.*
import no.trygdeetaten.skjema.oppdrag.Oppdrag

object Topics {
    val aap = Topic<AapUtbetaling>("aap.utbetalinger.v1", JsonSerde.jackson())
    val utbetalinger = Topic<Utbetaling>("helved.utbetalinger.v1", JsonSerde.jackson())
    val oppdrag = Topic<Oppdrag>("helved.oppdrag.v1", XmlSerde.serde())
    val status = Topic<StatusReply>("helved.status.v1", JsonSerde.jackson())
}

object Tables {
    val utbetalinger = Table(Topics.utbetalinger)
}

fun createTopology(): Topology = topology {
    val utbetalinger = consume(Tables.utbetalinger)
    consume(Topics.aap)
        .leftJoinWith(utbetalinger)
        .map { new, prev ->
            Result.catch<Pair<Utbetaling, Oppdrag>> {
                new.data.validate()
                new.data to when (new.action) {
                    Action.CREATE -> OppdragService.opprett(new.data, true) // TODO: join med e.g. sakid-topic
                    Action.UPDATE -> OppdragService.update(new.data, prev ?: notFound("previous utbetaling"))
                    Action.DELETE -> OppdragService.delete(new.data, prev ?: notFound("previous utbetaling"))
                }
            }
        }.branch({ it.isOk() }) {
            val result =  map { it -> it.unwrap() }
            result.map { (new, _) -> new }.produce(Topics.utbetalinger)
            result.map { (_, oppdrag) -> oppdrag }.produce(Topics.oppdrag)
            result.map { (_, _) -> StatusReply() }.produce(Topics.status)
        }.default {
            map { it -> StatusReply(Status.FEILET, it.unwrapErr()) }.produce(Topics.status)
        }
}
