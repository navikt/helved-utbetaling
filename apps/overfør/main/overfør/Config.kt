package overfør

import libs.kafka.StreamsConfig
import libs.mq.MQConfig
import libs.utils.env

data class Config(
    val kafka: StreamsConfig = StreamsConfig(),
    val oppdrag: OppdragConfig = OppdragConfig(),
    val mq: MQConfig = MQConfig(
        host = env("MQ_HOSTNAME"),
        port = env("MQ_PORT"),
        channel = env("MQ_CHANNEL"),
        manager = env("MQ_MANAGER"),
        username = "srvdp-oppdrag",
        password = env("MQ_PASSWORD"),
    ),
)

typealias Queue = String

data class OppdragConfig(
    val enabled: Boolean = env("OPPDRAG_ENABLED"),
    val kvitteringsKø: Queue = env("MQ_OPPDRAG_KVITTERING_QUEUE"),
    val sendKø: Queue = env("MQ_OPPDRAG_QUEUE"),
)

