package smokesignal

import libs.utils.env
import java.net.URI
import java.net.URL

data class Config(
    val azure: AzureConfig = AzureConfig(),
    val vedskiva: VedskivaConfig = VedskivaConfig()
)

data class VedskivaConfig(
    val scope: String = env("VEDSKIVA_SCOPE"),
    val host: URL = env("VEDSKIVA_HOST", URI("http://vedskiva").toURL())
)


data class AzureConfig(
    val tokenEndpoint: URL = env("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientId: String = env("AZURE_APP_CLIENT_ID"),
    val clientSecret: String = env("AZURE_APP_CLIENT_SECRET")
)
