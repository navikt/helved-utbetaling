package utsjekk

import libs.auth.AzureConfig
import libs.postgres.PostgresConfig

data class Config(
    val postgres: PostgresConfig = PostgresConfig(),
    val azure: AzureConfig = AzureConfig(),
)
