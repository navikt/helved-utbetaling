package smokesignal

import libs.auth.AzureConfig
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

