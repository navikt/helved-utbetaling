package utsjekk

import java.net.URI
import java.net.URL
import libs.auth.AzureConfig
import libs.kafka.StreamsConfig
import libs.jdbc.JdbcConfig
import libs.utils.env

data class Config(
    val jdbc: JdbcConfig = JdbcConfig(),
    val kafka: StreamsConfig = StreamsConfig(),
    val azure: AzureConfig = AzureConfig(),
    val oppdrag: OppdragConfig = OppdragConfig(),
    val simulering: SimuleringConfig = SimuleringConfig(),
    val abetal: AbetalConfig = AbetalConfig()
)

data class OppdragConfig(
    val scope: String = env("OPPDRAG_SCOPE"),
    val host: URL = env("OPPDRAG_HOST", URI("http://utsjekk-oppdrag").toURL())
)

data class SimuleringConfig(
    val scope: String = env("SIMULERING_SCOPE"),
    val host: URL = env("SIMULERING_HOST", URI("http://utsjekk-simulering").toURL())
)

data class AbetalConfig(
    val scope: String = env("ABETAL_SCOPE"),
    val host: URL = env("ABETAL_HOST", URI("http://abetal").toURL())
)
