package utsjekk

import libs.auth.AzureConfig
import libs.kafka.KafkaConfig
import libs.postgres.PostgresConfig
import libs.utils.env
import java.net.URI
import java.net.URL

data class Config(
    val postgres: PostgresConfig = PostgresConfig(),
    val kafka: KafkaConfig = KafkaConfig(),
    val azure: AzureConfig = AzureConfig(),
    val oppdrag: OppdragConfig = OppdragConfig(),
    val simulering: SimuleringConfig = SimuleringConfig(),
    val unleash: UnleashConfig = UnleashConfig(),
)

data class OppdragConfig(
    val scope: String = env("api://dev-gcp.helved.utsjekk-oppdrag/.default"),
    val host: URL = env("http://utsjekk-oppdrag")
)

data class SimuleringConfig(
    val scope: String = env("api://dev-gcp.helved.utsjekk-simulering/.default"),
    val host: URL = env("http://utsjekk-simulering")
)

data class UnleashConfig(
    val host: URI = env("UNLEASH_SERVER_API_URL"),
    val apiKey: String = env("UNLEASH_SERVER_API_TOKEN"),
    val appName: String = env("NAIS_APP_NAME"),
    val cluster: String = env("NAIS_CLUSTER_NAME")
)
