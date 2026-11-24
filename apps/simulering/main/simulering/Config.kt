package simulering

import libs.kafka.StreamsConfig
import libs.utils.env
import java.net.URI
import java.net.URL
import java.util.Properties

data class Config(
    val azure: AzureConfig = AzureConfig(),
    val simulering: SimuleringConfig = SimuleringConfig(),
    val kafka: StreamsConfig = StreamsConfig(
        additionalProperties = Properties().apply {
            // AdminClient trenger lengre tid ved opprettelse av internal topics
            this[org.apache.kafka.streams.StreamsConfig.RETRY_BACKOFF_MS_CONFIG] = 1000
            this[org.apache.kafka.streams.StreamsConfig.RECONNECT_BACKOFF_MS_CONFIG] = 1000
            this[org.apache.kafka.streams.StreamsConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG] = 5000
        }
    ),
)

data class SimuleringConfig(
    val proxy: ProxyConfig = ProxyConfig(),
    val soap: SoapConfig = SoapConfig(
        host = URI("${proxy.host}/${proxy.simuleringPath}").toURL(),
        sts = StsConfig(
            host = URI("${proxy.host}/gandalf").toURL(),
            user = "srvdp-simulering",
            pass = env("servicebruker_passord"),
        ),
    ),
    val sokos: SokosOppdragConfig = SokosOppdragConfig(),
)

data class SokosOppdragConfig(
    val cluster: String = env("NAIS_CLUSTER_NAME"),
    val host: URL = env("SOKOS_OPPDRAG_PROXY"),
    val scope: String = env("SOKOS_OPPDRAG_SCOPE")
)

data class ProxyConfig(
    val host: URL = env("PROXY_HOST"),
    val scope: String = env("PROXY_SCOPE"),
    val simuleringPath: String = env("SIMULERING_PATH"),
)
