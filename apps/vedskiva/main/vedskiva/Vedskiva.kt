package vedskiva

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import libs.auth.jwt
import libs.auth.TokenProvider
import libs.jdbc.Jdbc
import libs.jdbc.Migrator
import libs.jdbc.context
import libs.kafka.KafkaFactory
import libs.kafka.KafkaStreams
import libs.kafka.Streams
import libs.utils.appLog
import libs.utils.secureLog
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    embeddedServer(
        factory = Netty,
        configure = {
            shutdownGracePeriod = 5000L
            shutdownTimeout = 50_000L
            connectors.add(EngineConnectorBuilder().apply {
                port = 8080
            })
        },
        module = Application::vedskiva,
    ).start(wait = true)
}
// TODO: Skru ned CPU bruk i prod.yml!
fun Application.vedskiva(
    config: Config = Config(),
    streams: Streams = KafkaStreams(),
    kafka: KafkaFactory = Kafka(), // FIXME: streams har en innebygget ekvivalent
) {
    val metrics = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = metrics
        meterBinders += LogbackMetrics()
    }

    install(ContentNegotiation) {
        json(VedskivaKotlinx)
    }

    install(Authentication) {
        jwt(TokenProvider.AZURE, config.azure)
    }

    val jdbcCtx = Jdbc.initialize(config.jdbc).context()
    runBlocking {
        withContext(jdbcCtx) {
            Migrator(config.jdbc.migrations).migrate()
        }
    }

    val producer = kafka.createProducer(config.kafka, Topics.avstemming) 
    val service = AvstemmingService(config, producer)

    streams.connect(
        config = config.kafka,
        registry = metrics,
        topology = topology(jdbcCtx)
    )
    routing {
        authenticate(TokenProvider.AZURE) {
            avstem(service, jdbcCtx)
        }
        route("/actuator") {
            get("/metric") { call.respond(metrics.scrape()) }
            get("/health") { call.respond(HttpStatusCode.OK) }
        }
    }
}

object AvstemmingsdataSerializer : KSerializer<Avstemmingsdata> {
    private val xmlMapper = libs.xml.XMLMapper<Avstemmingsdata>()
    override val descriptor = PrimitiveSerialDescriptor("Avstemmingsdata", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Avstemmingsdata) = encoder.encodeString(xmlMapper.writeValueAsString(value))
    override fun deserialize(decoder: Decoder): Avstemmingsdata = xmlMapper.readValue(decoder.decodeString().toByteArray())!!
}

val VedskivaKotlinx = Json(libs.kotlinx.KotlinxJson) {
    serializersModule = SerializersModule {
        include(libs.kotlinx.KotlinxJson.serializersModule)
        contextual(Avstemmingsdata::class, AvstemmingsdataSerializer)
    }
}
