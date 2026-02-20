package peisschtappern

import io.ktor.server.auth.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.server.auth.jwt.*
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.auth.TokenProvider
import libs.auth.configure
import libs.kafka.KafkaStreams
import libs.kafka.Streams
import libs.jdbc.Jdbc
import libs.jdbc.Migrator
import libs.kafka.Topic
import libs.kafka.json
import libs.kafka.xml
import libs.utils.*
import models.DpUtbetaling
import models.TsDto
import models.Utbetaling
import no.trygdeetaten.skjema.oppdrag.Oppdrag

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}")
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
        module = Application::peisschtappern,
    ).start(wait = true)
}

fun Application.peisschtappern(
    config: Config = Config(),
    kafka: Streams = KafkaStreams(),
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(Authentication) {
        jwt(TokenProvider.AZURE) {
            configure(config.azure)
        }
    }

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    install(MicrometerMetrics) {
        registry = prometheus
        meterBinders += LogbackMetrics()
    }

    Jdbc.initialize(config.jdbc)
    runBlocking {
        withContext(Jdbc.context) {
            Migrator(config.jdbc.migrations).migrate()
        }
    }

    kafka.connect(
        topology = createTopology(config),
        config = config.kafka,
        registry = prometheus
    )

    val oppdragsdataProducer = kafka.createProducer(config.kafka, Topic("helved.oppdrag.v1", xml<Oppdrag>()))
    val utbetalingProducer = kafka.createProducer(config.kafka, Topic("helved.utbetalinger.v1", json<Utbetaling>()))
    val dpProducer = kafka.createProducer(config.kafka, Topic("helved.utbetalinger-dp.v1", json<DpUtbetaling>()))
    val tsProducer = kafka.createProducer(config.kafka, Topic("helved.utbetalinger-ts.v1", json<TsDto>()))
    val manuellEndringService = ManuellEndringService(oppdragsdataProducer, utbetalingProducer, dpProducer, tsProducer)

    routing {
        probes(kafka, prometheus)

        authenticate(TokenProvider.AZURE) {
            api(manuellEndringService)
        }
    }

    monitor.subscribe(ApplicationStopping) {
        kafka.close()
        oppdragsdataProducer.close()
    }

}

data class Audit(
    val ident: String,
    val name: String,
    val email: String,
    val reason: String?,
) {
    companion object {
        fun from(call: ApplicationCall, reason: String? = null): Audit {
            return Audit(
                name = call.claim("name") ?: "test",
                ident = call.claim("NAVident") ?: "test",
                email = call.claim("preferred_username") ?: "test",
                reason = reason
            )
        }
    }

    override fun toString(): String =
        if (reason != null) {
            "name:$name email:$email ident:$ident reason:$reason"
        } else {
            "name:$name email:$email ident:$ident"
        }
}

fun ApplicationCall.claim(claim: String): String? { 
    val principal = principal<JWTPrincipal>() ?: return null
    val claimValue = principal.payload.getClaim(claim)
    if (claimValue.isNull) {
        val claims = principal.payload.claims.keys.joinToString(", ")
        secureLog.info("could not find claim '$claim'. Available: [$claims]")
    }
    return when {
        claimValue.isNull -> null
        claimValue.asString() != null -> claimValue.toString()
        else -> claimValue.toString().replace("\"", "")
    }
}
