package urskog

import com.ibm.mq.jms.MQQueue
import java.net.URI
import java.net.URL
import java.util.Properties
import libs.auth.AzureConfig
import libs.kafka.StreamsConfig
import libs.mq.MQConfig
import libs.utils.env
import libs.ws.SoapConfig
import libs.ws.StsConfig
import org.apache.kafka.clients.consumer.ConsumerConfig

data class Config(
    val kafka: StreamsConfig = StreamsConfig(
        additionalProperties = Properties().apply {
            // Vi har 3 partisjoner, for å ha en standby-replica må vi ha 4 poder.
            // For å bruke 1 pod, kan vi ikke lenger ha noen standby-replicas
            this[org.apache.kafka.streams.StreamsConfig.NUM_STANDBY_REPLICAS_CONFIG] = 0

            // Vi har 3 partisjoner, hver trenger en tråd på en egen CPU. 
            // Derfor trenger i 3000m CPU og -XX:ActiveProcessorCount=3
            this[org.apache.kafka.streams.StreamsConfig.NUM_STREAM_THREADS_CONFIG] = 3

            // AdminClient trenger lengre tid ved opprettelse av internal topics
            this[org.apache.kafka.streams.StreamsConfig.RETRY_BACKOFF_MS_CONFIG] = 5000
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
    val simulering: SoapConfig = SoapConfig(
        host = URI("${proxy.host}/${env<String>("SIMULERING_PATH")}").toURL(),
        sts = StsConfig(
            host = URI("${proxy.host}/gandalf").toURL(),
            user = "srvdp-simulering",
            pass = env("servicebruker_passord"), // from secret utsjekk-oppdrag-simulering
        ),
    ),
)

data class OppdragConfig(
    val kvitteringsKø: MQQueue = MQQueue(env("MQ_OPPDRAG_KVITTERING_QUEUE")),
    val sendKø: MQQueue = MQQueue(env("MQ_OPPDRAG_QUEUE")),
    val avstemmingKø: MQQueue = MQQueue(env("MQ_AVSTEMMING_QUEUE")).apply {
        targetClient = 1 // Skru av JMS-headere, da OS ikke støtter disse for avstemming
    }
)

data class ProxyConfig(
    val host: URL = env("PROXY_HOST"),
    val scope: String = env("PROXY_SCOPE"),
)
