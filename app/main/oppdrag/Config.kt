package oppdrag

import libs.auth.AzureConfig
import libs.utils.env

data class Config(
    val avstemming: AvstemmingConfig = AvstemmingConfig(),
    val oppdrag: OppdragConfig = OppdragConfig(),
    val postgres: PostgresConfig = PostgresConfig(),
    val azure: AzureConfig = AzureConfig(),
    val mq: MQConfig = MQConfig(),
)

data class AvstemmingConfig(
    val enabled: Boolean = env("AVSTEMMING_ENABLED"),
    val utKø: Queue = env("AVSTEMMING_KØ"),
)

data class OppdragConfig(
    val enabled: Boolean = env("OPPDRAG_ENABLED"),
    val kvitteringsKø: Queue = env("OPPDRAG_KVITTERINGSKØ"),
    val sendKø: Queue = env("OPPDRAG_SENDKØ"),
)

typealias Queue = String

data class MQConfig(
    val host: String = env("MQ_HOSTNAME"),
    val port: Int = env("MQ_PORT"),
    val channel: String = env("MQ_CHANNEL"),
    val manager: String = env("MQ_QUEUE_MANAGER"),
    val username: String = env("SERVICEUSER_NAME"),
    val password: String = env("SERVICEUSER_PASSWORD"),
)

data class PostgresConfig(
    val host: String = env("NAIS_DATABASE_INNSENDING_INNSENDING_HOST"),
    val port: String = env("NAIS_DATABASE_INNSENDING_INNSENDING_PORT"),
    val database: String = env("NAIS_DATABASE_INNSENDING_INNSENDING_DATABASE"),
    val username: String = env("NAIS_DATABASE_INNSENDING_INNSENDING_USERNAME"),
    val password: String = env("NAIS_DATABASE_INNSENDING_INNSENDING_PASSWORD"),
)