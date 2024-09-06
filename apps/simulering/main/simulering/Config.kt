package simulering

import libs.auth.AzureConfig
import libs.utils.env
import libs.ws.SoapConfig
import libs.ws.StsConfig
import java.net.URI
import java.net.URL

data class Config(
    val proxy: ProxyConfig = ProxyConfig(),
    val azure: AzureConfig = AzureConfig(),
    val simulering: SoapConfig = SoapConfig(
        host = URI("${proxy.host}/${proxy.simuleringPath}").toURL(),
        sts = StsConfig(
            host = URI("${proxy.host}/gandalf").toURL(),
            user = "srvdp-simulering",
            pass = env("servicebruker_passord") // from secret utsjekk-oppdrag-simulering
        )
    ),
)

data class ProxyConfig(
    val host: URL = env("PROXY_HOST"),
    val scope: String = env("PROXY_SCOPE"),
    val simuleringPath: String = env("SIMULERING_PATH")
)
