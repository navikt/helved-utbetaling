package urskog

import com.ibm.mq.jms.MQQueue
import libs.kafka.SslConfig
import libs.kafka.StreamsConfig
import libs.mq.MQConfig
import libs.auth.AzureConfig
import libs.ws.SoapConfig

object TestConfig {
    fun create(
        proxy: ProxyConfig,
        azure: AzureConfig,
        simulering: SoapConfig,
    ): Config {
        val oppdrag = OppdragConfig(
            avstemmingKø = MQQueue("DEV.QUEUE.3"),
            kvitteringsKø = MQQueue("DEV.QUEUE.2"),
            sendKø = MQQueue("DEV.QUEUE.1")
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

