package abetal

import libs.kafka.*
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import abetal.models.*

object Topics {
    val aap = Topic<Utbetaling>("aap.utbetalinger.v1", JsonSerde.jackson())
    val utbetalinger = Topic<Utbetaling>("helved.utbetalinger.v1", JsonSerde.jackson())
    val simuleringer = Topic<Utbetaling>("helved.utbetalinger.v1", JsonSerde.jackson())
    val status = Topic<StatusReply>("helved.status.v1", JsonSerde.jackson())
}

fun createTopology(): Topology = topology {
    val aap = consume(Topics.aap)
    aap.produce(Topics.utbetalinger)
    aap.map { _ -> StatusReply() }.produce(Topics.status)
}


