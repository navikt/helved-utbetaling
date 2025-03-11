package urskog

import java.net.URI
import java.net.URL
import libs.auth.AzureConfig
import libs.kafka.StreamsConfig
import libs.mq.MQConfig
import libs.utils.env
import libs.ws.SoapConfig
import libs.ws.StsConfig

data class Config(
    val kafka: StreamsConfig = StreamsConfig(),
    val oppdrag: OppdragConfig = OppdragConfig(),
    val mq: MQConfig = MQConfig(
        host = env("MQ_HOSTNAME"),
        port = env("MQ_PORT"),
        channel = env("MQ_CHANNEL"),
        manager = env("MQ_MANAGER"),
        username = "srvdp-oppdrag",
        password = env("MQ_PASSWORD"), // from secret utsjekk-oppdrag
    ),
    val proxy: ProxyConfig = ProxyConfig(),
    val azure: AzureConfig = AzureConfig(),
    val simulering: SoapConfig = SoapConfig(
        host = URI("${proxy.host}/${env<String>("SIMULERING_PATH")}").toURL(),
        sts = StsConfig(
            host = URI("${proxy.host}/gandalf").toURL(),
            user = "srvdp-simulering",
            pass = env("servicebruker_passord"), // from secret utsjekk-oppdrag-simulering
        ),
    ),
)

typealias Queue = String

data class OppdragConfig(
    val kvitteringsKø: Queue = env("MQ_OPPDRAG_KVITTERING_QUEUE"),
    val sendKø: Queue = env("MQ_OPPDRAG_QUEUE"),
)

data class ProxyConfig(
    val host: URL = env("PROXY_HOST"),
    val scope: String = env("PROXY_SCOPE"),
)
