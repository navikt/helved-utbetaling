package urskog

import com.ibm.mq.jms.MQQueue
import libs.auth.AzureConfig
import libs.jdbc.JdbcConfig
import libs.kafka.StreamsConfig
import libs.mq.MQConfig
import libs.utils.env
import java.io.File
import java.net.URL
import java.util.*

data class Config(
    val jdbc : JdbcConfig = JdbcConfig(
        url = env("DB_JDBC_URL"), // databaser provisjonert etter juni 2024 må bruke denne
        migrations = listOf(File("migrations")),
    ),
    val kafka: StreamsConfig = StreamsConfig(
        additionalProperties = Properties().apply {
            // AdminClient trenger lengre tid ved opprettelse av internal topics
            this[org.apache.kafka.streams.StreamsConfig.RETRY_BACKOFF_MS_CONFIG] = 1000
            this[org.apache.kafka.streams.StreamsConfig.RECONNECT_BACKOFF_MS_CONFIG] = 1000
        }
    ),
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
    val cluster: String = env("NAIS_CLUSTER_NAME"),
)

data class OppdragConfig(
    val kvitteringsKø: MQQueue = MQQueue(env("MQ_OPPDRAG_KVITTERING_QUEUE")),
    val sendKø: MQQueue = MQQueue(env("MQ_OPPDRAG_QUEUE")),
    val darePocAapKø: MQQueue = MQQueue(env("MQ_DARE_POC_AAP_QUEUE")),
    val avstemmingKø: MQQueue = MQQueue(env("MQ_AVSTEMMING_QUEUE")).apply {
        targetClient = 1 // Skru av JMS-headere, da OS ikke støtter disse for avstemming
    }
)

data class ProxyConfig(
    val host: URL = env("PROXY_HOST"),
    val scope: String = env("PROXY_SCOPE"),
)
