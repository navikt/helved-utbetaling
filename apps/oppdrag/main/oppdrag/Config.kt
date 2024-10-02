package oppdrag

import libs.auth.AzureConfig
import libs.mq.MQConfig
import libs.postgres.JdbcConfig
import libs.utils.env

data class Config(
    val avstemming: AvstemmingConfig = AvstemmingConfig(),
    val oppdrag: OppdragConfig = OppdragConfig(),
    val postgres: JdbcConfig = JdbcConfig(),
    val azure: AzureConfig = AzureConfig(),
    val mq: MQConfig = MQConfig(
        host = env("MQ_HOSTNAME"),
        port = env("MQ_PORT"),
        channel = env("MQ_CHANNEL"),
        manager = env("MQ_MANAGER"),
        username = "srvdp-oppdrag",
        password = env("MQ_PASSWORD"),
    ),
)

data class AvstemmingConfig(
    val enabled: Boolean = env("AVSTEMMING_ENABLED"),
    val utKø: Queue = env("MQ_AVSTEMMING_QUEUE"),
)

data class OppdragConfig(
    val enabled: Boolean = env("OPPDRAG_ENABLED"),
    val kvitteringsKø: Queue = env("MQ_OPPDRAG_KVITTERING_QUEUE"),
    val sendKø: Queue = env("MQ_OPPDRAG_QUEUE"),
)

typealias Queue = String
