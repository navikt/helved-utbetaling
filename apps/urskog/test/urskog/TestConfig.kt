package urskog

import libs.kafka.SslConfig
import libs.kafka.StreamsConfig
import libs.mq.MQConfig
import libs.auth.AzureConfig
import libs.ws.SoapConfig
import libs.ws.StsConfig
import libs.utils.env
import java.net.URI

object TestConfig {
    fun create(
        proxy: ProxyConfig,
        azure: AzureConfig,
        simulering: SoapConfig,
    ): Config {
        val oppdrag = OppdragConfig(
            kvitteringsKø = "DEV.QUEUE.2",
            sendKø = "DEV.QUEUE.1"
        )
        val kafka = StreamsConfig(
            "",
            "",
            SslConfig("", "", ""),
        )
        val mq = MQConfig(
            host = "og hark",
            port = 99,
            channel = "",
            manager = "anders",
            username = "",
            password = "",
        )

        return Config(
            kafka = kafka,
            oppdrag = oppdrag,
            mq = mq,
            proxy = proxy,
            azure = azure,
            simulering = simulering,
        )
    }
}

