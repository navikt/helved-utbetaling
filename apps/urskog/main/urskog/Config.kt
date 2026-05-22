package urskog

import com.ibm.mq.jms.MQQueue
import libs.auth.AzureConfig
import libs.jdbc.JdbcConfig
import libs.kafka.StreamsConfig
import libs.mq.MQConfig
import libs.utils.env
import libs.ws.SoapConfig
import libs.ws.StsConfig
import java.io.File
import java.net.URI
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

            // Simulering gjøres synkront i topology via runBlocking { soap.call(...) }.
            // SOAP-klienten i SimuleringService har requestTimeoutMs = 120_000 (2 min) worst case.
            // Default max.poll.interval.ms i Kafka Streams er 5 min, og default max.poll.records er
            // høyt nok til at 2-3 worst-case-kall i samme batch overskrider intervallet og fører til
            // at consumer kastes ut av gruppen. Det igjen gjør at produsenten fences under EOS_v2
            // (InvalidProducerEpochException / ProducerFencedException). Se WS.kt for SOAP-timeout.
            // Vi prefikser med consumerPrefix slik at kun main streams consumer påvirkes
            // (restore consumer og global consumer beholder defaults).
            this[org.apache.kafka.streams.StreamsConfig.consumerPrefix(
                org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG
            )] = 600_000 // 10 min, gir rom for 2 min SOAP + GC + DB + buffer

            this[org.apache.kafka.streams.StreamsConfig.consumerPrefix(
                org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG
            )] = 1 // én simulering per poll-syklus, så worst-case interval er bundet til ~2 min

            // EOS_v2 åpner én Kafka-transaksjon per poll-syklus. Broker default
            // transaction.timeout.ms = 60_000, mens SOAP-kall i simulering-topologien
            // kan blokkere stream-tråden i opptil 120_000 ms (se WS.kt requestTimeoutMs).
            // Når åpne transaksjoner overskrider broker-timeouten bumper broker
            // producer-epoch -> InvalidProducerEpochException / ProducerFencedException /
            // TaskMigratedException -> rebalance-loop.
            // Vi setter 3 min = 2 min SOAP + buffer for commit/ack/GC.
            // NB: må være <= broker transaction.max.timeout.ms (Aiven default 900_000).
            this[org.apache.kafka.streams.StreamsConfig.producerPrefix(
                org.apache.kafka.clients.producer.ProducerConfig.TRANSACTION_TIMEOUT_CONFIG
            )] = 180_000
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
