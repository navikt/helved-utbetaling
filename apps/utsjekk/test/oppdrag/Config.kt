package oppdrag

import libs.auth.AzureConfig
import libs.mq.MQConfig
import libs.utils.env

fun testConfig(
    postgres: PostgresConfig,
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
    mq = mq,
)

fun isGHA(): Boolean {
    return runCatching {
        env<Boolean>("GITHUB_ACTIONS")
    } .getOrDefault(false)
}