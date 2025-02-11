package abetal

import libs.kafka.*
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import abetal.models.*

object Topics {
    val aap = Topic<Utbetaling>("aap.utbetalinger.v1", JsonSerde.jackson())
    val utbetalinger = Topic<Utbetaling>("helved.utbetalinger.v1", JsonSerde.jackson())
    val status = Topic<StatusReply>("helved.status.v1", JsonSerde.jackson())
}

fun createTopology(): Topology = topology {
    consume(Topics.aap)
        .map { utbet -> 
            try {
                utbet.validate()
                utbet to null
            } catch(err: ApiError) {
                utbet to err
            }
        }.branch({ (_, err) -> err != null}) {
            this.map { (_, err) -> StatusReply(error = err) }.produce(Topics.status)
        }.default {
            val aap = this.map { (utbet, _) -> utbet}
            aap.produce(Topics.utbetalinger)
            aap.map{ _ -> StatusReply() }.produce(Topics.status)
        }
}

fun Utbetaling.validate() {
    badRequest("ikke bra")
}

