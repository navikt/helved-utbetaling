package utsjekk

import libs.auth.AzureConfig
import libs.postgres.PostgresConfig
import libs.utils.env
import java.net.URI
import java.net.URL

data class Config(
    val postgres: PostgresConfig = PostgresConfig(),
    val azure: AzureConfig = AzureConfig(),
    val oppdrag: OppdragConfig = OppdragConfig(),
    val unleash: UnleashConfig = UnleashConfig(),
)

data class OppdragConfig(
    val scope: String = env("http://helved-utsjekk"),
    val host: URL = env("api://dev-gcp.helved.utsjekk-simulering/.default")
)

data class UnleashConfig(
    val host: URI = env("UNLEASH_SERVER_API_URL"),
    val apiKey: String = env("UNLEASH_SERVER_API_TOKEN"),
    val appName: String = env("NAIS_APP_NAME"),
    val cluster: String = env("NAIS_CLUSTER_NAME")
)
