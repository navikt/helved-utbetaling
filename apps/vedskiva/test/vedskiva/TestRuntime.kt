package vedskiva

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import libs.jdbc.Jdbc
import libs.jdbc.PostgresContainer
import libs.jdbc.migrateTemplate
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.truncate
import libs.kafka.*
import libs.ktor.KtorRuntime
import libs.utils.logger
import java.io.File
import java.net.URI
import javax.sql.DataSource

val testLog = logger("test")

class TestTopics(kafka: StreamsMock) {
    val oppdrag = kafka.testTopic(Topics.oppdrag) 
}

object TestRuntime {
    private val migrationDirs = listOf(File("test/migrations"), File("migrations"))
    private val postgres: PostgresContainer by lazy {
        PostgresContainer(
            appname = "vedskiva",
            migrationDirs = migrationDirs,
            migrate = ::migrateTemplate,
        )
    }
    val azure: AzureFake by lazy { AzureFake() }
    val peisschtappern: PeisschtappernFake by lazy { PeisschtappernFake() }
    private val kafkaFake: KafkaFactoryFake by lazy { KafkaFactoryFake() }
    val kafka: KafkaFactoryFake
        get() {
            ktor // ensures producers/consumers are registered with the factory
            return kafkaFake
        }
    val streams: StreamsMock by lazy { StreamsMock() }
    val jdbc: DataSource by lazy { Jdbc.initialize(postgres.config) }
    val context: CoroutineDatasource by lazy { CoroutineDatasource(jdbc) }
    val config: Config by lazy {
        Config(
            kafka = StreamsConfig("test-application", "localhost:9092", SslConfig("", "", "")),
            jdbc = postgres.config,
            azure = azure.config,
            peisschtappern.config,
        )
    }


    val ktor: KtorRuntime<Config> by lazy {
        KtorRuntime<Config>(
            appName = "vedskiva",
            module = {
                vedskiva(
                    config,
                    streams,
                    kafkaFake,
                )
            },
            onClose = {
                reset()
                postgres.close()
            },
        )
    }

    fun reset() {
        jdbc.truncate("vedskiva", Scheduled.TABLE_NAME, OppdragDao.table)
        kafkaFake.reset()
        PeisschtappernFake.response.clear()
    }
}

class AzureFake {
    private val server = KtorRuntime<Nothing>("vedskiva.azure", AzureFake::azure)

    fun generateToken() = jwksGenerator.generate()

    companion object {
        fun azure(app: Application) {
            app.install(ContentNegotiation) { jackson() }
            app.routing {
                get("/jwks") {
                    call.respondText(libs.auth.TEST_JWKS)
                }

                post("/token") {
                    call.respond(libs.auth.AzureToken(3600, "token"))
                }
            }
        }
    }

    val config by lazy {
        libs.auth.AzureConfig(
            tokenEndpoint = "http://localhost:${server.port}/token".let(::URI).toURL(),
            jwks = "http://localhost:${server.port}/jwks".let(::URI).toURL(),
            issuer = "test",
            clientId = "hei",
            clientSecret = "på deg"
        )
    }
    private val jwksGenerator = libs.auth.JwkGenerator(config.issuer, config.clientId)
}

class PeisschtappernFake {
    private val server = KtorRuntime<Nothing>("vedskiva.peisschtappern", PeisschtappernFake::server)

    companion object {
        val response = mutableListOf<Dao>()
        fun server(app: Application) {
            app.install(ContentNegotiation) { 
                jackson {
                    registerModule(JavaTimeModule())
                    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                }
            }
            app.routing {
                get("/api/messages") {
                    if (response.isNotEmpty()) {
                        call.respond(mapOf("items" to response, "total" to response.size))
                    } else {
                        call.respondText(libs.utils.Resource.read("/may_5th.json"), ContentType.Application.Json)
                    }
                }
            }
        }
    }

    val config by lazy {
        PeisschtappernConfig(
            host = "http://localhost:${server.port}".let(::URI).toURL(),
            scope = "test"
        )
    }
}

@Suppress("UNCHECKED_CAST")
class KafkaFactoryFake: KafkaFactory {
    private val producers = mutableMapOf<String, KafkaProducerFake<*, * >>()
    private val consumers = mutableMapOf<String, KafkaConsumerFake<*, * >>()

    override fun <K: Any, V> createProducer(
        config: StreamsConfig,
        topic: Topic<K, V & Any>,
    ): KafkaProducerFake<K, V> {
        if (producers.containsKey(topic.name)) error("producer already registered for $topic")
        producers[topic.name] = KafkaProducerFake(topic)
        return producers[topic.name]!! as KafkaProducerFake<K, V>
    }

    override fun <K: Any, V> createConsumer(
        config: StreamsConfig,
        topic: Topic<K, V & Any>,
        resetPolicy: OffsetResetPolicy,  
        maxProcessingTimeMs: Int,
        groupId: Int,
    ): KafkaConsumerFake<K, V> {
        consumers[topic.name] = KafkaConsumerFake(topic)
        return consumers[topic.name]!! as KafkaConsumerFake<K, V>
    }

    fun reset() {
        producers.values.forEach { topic -> 
            topic.clear()
        }
    }

    fun <K: Any, V> getProducer(topic: Topic<K, V & Any>): KafkaProducerFake<K, V> {
        return producers[topic.name]!! as KafkaProducerFake<K, V>
    }
}

