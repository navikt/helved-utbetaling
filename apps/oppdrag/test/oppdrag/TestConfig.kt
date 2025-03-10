package oppdrag

import libs.auth.AzureConfig
import libs.mq.MQConfig
import libs.postgres.JdbcConfig

object TestConfig {
    fun create(
        postgres: JdbcConfig,
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
        mq = MQConfig(
            host = "og hark",
            port = 99,
            channel = "",
            manager = "anders",
            username = "",
            password = "",
        ),
    )
}
