package urskog

import libs.kafka.SslConfig
import libs.kafka.StreamsConfig
import libs.mq.MQConfig

object TestConfig {
    fun create(
        mq: MQConfig,
    ): Config = Config(
        oppdrag = OppdragConfig(
            enabled = true,
            kvitteringsKø = "DEV.QUEUE.2",
            sendKø = "DEV.QUEUE.1"
        ),
        mq = mq,
        kafka = StreamsConfig("", "", SslConfig("", "", "")),
    )
}

