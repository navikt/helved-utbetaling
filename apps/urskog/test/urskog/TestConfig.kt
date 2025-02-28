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
        mq: MQConfig,
        proxyPort: Int,
        azurePort: Int,
    ): Config {
        val proxy = ProxyConfig(
            host = "http://localhost:$proxyPort".let(::URI).toURL(),
            scope = "test",
        )
        val oppdrag = OppdragConfig(
            kvitteringsKø = "DEV.QUEUE.2",
            sendKø = "DEV.QUEUE.1"
        )
        val kafka = StreamsConfig(
            "",
            "",
            SslConfig("", "", ""),
        )
        val azure = AzureConfig(
            tokenEndpoint = "http://localhost:$azurePort/token".let(::URI).toURL(),
            jwks = "http://localhost:$azurePort/jwks".let(::URI).toURL(),
            issuer = "test",
            clientId = "",
            clientSecret = ""
        )
        val sts = StsConfig(
            host = "${proxy.host}/gandalf".let(::URI).toURL(),
            user = "",
            pass = "",
        )
        val simulering = SoapConfig(
            host = "${proxy.host}/cics/oppdrag/simulerFpServiceWSBinding".let(::URI).toURL(),
            sts = sts,
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

