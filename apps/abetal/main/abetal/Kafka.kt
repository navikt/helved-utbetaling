package abetal

import abetal.models.*
import libs.kafka.*

object Topics {
    val aap = Topic<AapUtbetaling>("aap.utbetalinger.v1", JsonSerde.jackson())
    val utbetalinger = Topic<Utbetaling>("helved.utbetalinger.v1", JsonSerde.jackson())
    val status = Topic<StatusReply>("helved.status.v1", JsonSerde.jackson())
}

fun createTopology(): Topology = topology {
    consume(Topics.aap)
        .map { utbet ->
            try {
                utbet.data.validate()
                utbet
            } catch (err: ApiError) {
                utbet.apply { error = err }
            }
        }.branch({ utbet -> utbet.error != null }) {
            this.map { utbet -> StatusReply(error = utbet.error) }.produce(Topics.status)
        }.default {
            val aap = this.map { utbet -> utbet.data }
            aap.produce(Topics.utbetalinger)
            aap.map { _ -> StatusReply() }.produce(Topics.status)
        }
}
