package oppdrag

data class Config(
    val avstemming: AvstemmingConfig = AvstemmingConfig(),
    val oppdrag: OppdragConfig = OppdragConfig(),
    val postgres: PostgresConfig = PostgresConfig()
)

data class AvstemmingConfig(
    val enabled: Boolean = envVar("AVSTEMMING_ENABLED").toBoolean()
)

data class OppdragConfig(
    val enabled: Boolean = envVar("OPPDRAG_ENABLED").toBoolean(),
    val mq: MQConfig = MQConfig(),
    val kvitteringsKø: Queue = envVar("OPPDRAG_KVITTERINGSKØ"),
    val sendKø: Queue = envVar("OPPDRAG_SENDKØ"),
)

typealias Queue = String

data class MQConfig(
    val host: String = envVar("MQ_HOSTNAME"),
    val port: Int = envVar("MQ_PORT").toInt(),
    val channel: String = envVar("MQ_CHANNEL"),
    val manager: String = envVar("MQ_QUEUE_MANAGER"),
    val username: String = envVar("SERVICEUSER_NAME"),
    val password: String = envVar("SERVICEUSER_PASSWORD"),
)

data class PostgresConfig(
    val host: String = envVar("NAIS_DATABASE_INNSENDING_INNSENDING_HOST"),
    val port: String = envVar("NAIS_DATABASE_INNSENDING_INNSENDING_PORT"),
    val database: String = envVar("NAIS_DATABASE_INNSENDING_INNSENDING_DATABASE"),
    val username: String = envVar("NAIS_DATABASE_INNSENDING_INNSENDING_USERNAME"),
    val password: String = envVar("NAIS_DATABASE_INNSENDING_INNSENDING_PASSWORD"),
)