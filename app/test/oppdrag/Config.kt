package oppdrag

import libs.auth.AzureConfig

fun testConfig(
    postgres: PostgresConfig,
    mq: MQConfig,
    azureConfig: AzureConfig,
): Config = Config(
    avstemming = AvstemmingConfig(
        enabled = true,
    ),
    oppdrag = OppdragConfig(
        enabled = true,
        mq = mq,
        kvitteringsKø = "DEV.QUEUE.2",
        sendKø = "DEV.QUEUE.1"
    ),
    postgres = postgres,
    azure = azureConfig,
)