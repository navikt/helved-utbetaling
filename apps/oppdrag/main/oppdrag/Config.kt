package oppdrag

import libs.auth.AzureConfig
import libs.mq.MQConfig
import libs.utils.env

data class Config(
    val avstemming: AvstemmingConfig = AvstemmingConfig(),
    val oppdrag: OppdragConfig = OppdragConfig(),
    val postgres: PostgresConfig = PostgresConfig(),
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

data class PostgresConfig(
    val host: String = env("DB_HOST"),
    val port: String = env("DB_PORT"),
    val database: String = env("DB_DATABASE"),
    val username: String = env("DB_USERNAME"),
    val password: String = env("DB_PASSWORD"),
)
