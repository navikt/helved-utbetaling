package simulering

import libs.kafka.StreamsConfig
import libs.utils.env
import java.net.URI
import java.net.URL
import java.util.Properties

data class Config(
    val proxy: ProxyConfig = ProxyConfig(),
    val azure: AzureConfig = AzureConfig(),
    val simulering: SoapConfig = SoapConfig(
        host = URI("${proxy.host}/${proxy.simuleringPath}").toURL(),
        sts = StsConfig(
            host = URI("${proxy.host}/gandalf").toURL(),
            user = "srvdp-simulering",
            pass = env("servicebruker_passord"),
        ),
    ),
    val kafka: StreamsConfig = StreamsConfig(
        additionalProperties = Properties().apply {
            // AdminClient trenger lengre tid ved opprettelse av internal topics
            this[org.apache.kafka.streams.StreamsConfig.RETRY_BACKOFF_MS_CONFIG] = 1000
            this[org.apache.kafka.streams.StreamsConfig.RECONNECT_BACKOFF_MS_CONFIG] = 1000
            this[org.apache.kafka.streams.StreamsConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG] = 5000
        }
    ),
)

data class ProxyConfig(
    val host: URL = env("PROXY_HOST"),
    val scope: String = env("PROXY_SCOPE"),
    val simuleringPath: String = env("SIMULERING_PATH"),
)
