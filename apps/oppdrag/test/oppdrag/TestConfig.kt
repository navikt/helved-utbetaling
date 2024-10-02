package oppdrag

import libs.auth.AzureConfig
import libs.mq.MQConfig
import libs.postgres.JdbcConfig
import libs.utils.env

object TestConfig {
    fun create(
        postgres: JdbcConfig,
        mq: MQConfig,
        azureConfig: AzureConfig,
    ): Config = Config(
        avstemming = AvstemmingConfig(
            enabled = true,
            utKø = "DEV.QUEUE.3"
        ),
        oppdrag = OppdragConfig(
            enabled = true,
            kvitteringsKø = "DEV.QUEUE.2",
            sendKø = "DEV.QUEUE.1"
        ),
        postgres = postgres,
        azure = azureConfig,
        mq = mq
    )
}

fun isGHA(): Boolean = env("GITHUB_ACTIONS", false)
