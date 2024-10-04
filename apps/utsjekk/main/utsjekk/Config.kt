package utsjekk

import libs.auth.AzureConfig
import libs.kafka.KafkaConfig
import libs.postgres.JdbcConfig
import libs.utils.env
import java.net.URI
import java.net.URL

data class Config(
    val jdbc: JdbcConfig = JdbcConfig(),
    val kafka: KafkaConfig = KafkaConfig(),
    val azure: AzureConfig = AzureConfig(),
    val oppdrag: OppdragConfig = OppdragConfig(),
    val simulering: SimuleringConfig = SimuleringConfig(),
    val unleash: UnleashConfig = UnleashConfig(),
    val electorUrl: URL = env<URL>("ELECTOR_GET_URL")
)

data class OppdragConfig(
    val scope: String = env("OPPDRAG_SCOPE", "api://dev-gcp.helved.utsjekk-oppdrag/.default"),
    val host: URL = env("OPPDRAG_HOST", URI("http://utsjekk-oppdrag").toURL())
)

data class SimuleringConfig(
    val scope: String = env("SIMULERING_SCOPE", "api://dev-gcp.helved.utsjekk-simulering/.default"),
    val host: URL = env("SIMULERING_HOST", URI("http://utsjekk-simulering").toURL())
)

data class UnleashConfig(
    val host: URI = env("UNLEASH_SERVER_API_URL"),
    val apiKey: String = env("UNLEASH_SERVER_API_TOKEN"),
    val appName: String = env("NAIS_APP_NAME"),
    val cluster: String = env("NAIS_CLUSTER_NAME")
)
