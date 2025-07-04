package vedskiva

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import libs.jdbc.*
import libs.kafka.*
import libs.ktor.*
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.*
import libs.utils.logger
import java.io.File
import java.net.URI

val testLog = logger("test")

object TestRuntime {

    private val postgres = PostgresContainer("vedskiva")
    val azure = AzureFake()
    val peisschtappern = PeisschtappernFake()
    val kafka = KafkaFactoryFake()
    val jdbc = Jdbc.initialize(postgres.config)
    val context = CoroutineDatasource(jdbc)
    val config = Config(
        kafka = StreamsConfig("", "", SslConfig("", "", "")),
        jdbc = postgres.config.copy(migrations = listOf(File("test/migrations"), File("migrations"))),
        azure = azure.config,
        peisschtappern.config,
    )

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            reset()
            postgres.close()
        })
    }

    fun reset() {
        jdbc.truncate("vedskiva", Scheduled.TABLE_NAME)
        kafka.reset()
        PeisschtappernFake.response.clear()
    }
}

val http: HttpClient by lazy {
    HttpClient()
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
                get("/api") {
                    if (response.isNotEmpty()) {
                        call.respond(response)
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
    private val producers = mutableMapOf<String, KafkaProducer<*, * >>()
    private val consumers = mutableMapOf<String, KafkaConsumer<*, * >>()

    internal fun reset() {
        producers.clear()
        consumers.clear()
    }

    override fun <K: Any, V> createProducer(
        config: StreamsConfig,
        topic: Topic<K, V & Any>,
    ): KafkaProducerFake<K, V> {
        return producers.getOrPut(topic.name) { KafkaProducerFake(topic) } as KafkaProducerFake<K, V>
    }

    override fun <K: Any, V> createConsumer(
        config: StreamsConfig,
        topic: Topic<K, V & Any>,
        resetPolicy: OffsetResetPolicy,  
        maxProcessingTimeMs: Int,
        groupId: Int,
    ): KafkaConsumerFake<K, V> {
        return consumers.getOrPut(topic.name) { KafkaConsumerFake(topic) } as KafkaConsumerFake<K, V>
    }
}

