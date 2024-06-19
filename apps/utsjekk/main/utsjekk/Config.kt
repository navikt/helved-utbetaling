package utsjekk

import libs.auth.AzureConfig
import libs.postgres.PostgresConfig
import libs.utils.env
import java.net.URL

data class Config(
    val postgres: PostgresConfig = PostgresConfig(),
    val azure: AzureConfig = AzureConfig(),
    val oppdrag: OppdragConfig = OppdragConfig(),
)

data class OppdragConfig(
    val scope: String = env("http://helved-utsjekk"),
    val host: URL = env("api://dev-gcp.helved.utsjekk-simulering/.default")
)
