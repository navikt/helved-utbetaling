package branntaarn

import java.net.URI
import java.net.URL
import libs.auth.AzureConfig
import libs.utils.env

data class Config(
    val azure: AzureConfig = AzureConfig(),
    val peisschtappern: PeisschtappernConfig = PeisschtappernConfig(),
    val peisen: PeisenConfig = PeisenConfig(),
    val slack: SlackConfig = SlackConfig(),
    val nais: NaisConfig = NaisConfig(),
)

data class PeisschtappernConfig(
    val scope: String = env("PEISSCHTAPPERN_SCOPE"),
    val host: URL = env("PEISSCHTAPPERN_HOST", URI("http://peisschtappern").toURL())
)

data class PeisenConfig(
    val host: URL = env("PEISEN_HOST")
)

data class SlackConfig(
    val host: URL = env("apiUrl"),
)

data class NaisConfig(
    val cluster: String = env("NAIS_CLUSTER_NAME"),
)
