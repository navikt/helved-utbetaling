package utsjekk.simulering

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.kafka.KafkaProducer
import models.DpUtbetaling
import models.Fagsystem
import models.Simulering
import models.UtbetalingId
import utsjekk.*
import utsjekk.utbetaling.Utbetaling
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

object SimuleringSubscriptions {

    private val subscriptions = ConcurrentHashMap<String, CompletableDeferred<Simulering>>()
    val subscriptionEvents = Channel<String>(Channel.UNLIMITED)

    fun subscribe(key: String): CompletableDeferred<Simulering> {
        if (subscriptions.containsKey(key)) locked("Simulering for $key pågår allerede")
        val completableDeferred = CompletableDeferred<Simulering>()
        subscriptions[key] = completableDeferred
        subscriptionEvents.trySend(key)
        return completableDeferred
    }

    fun unsubscribe(key: String) {
        subscriptions[key]?.cancel()
        subscriptions.remove(key)
    }

    fun complete(key: String, simulering: Simulering) {
        subscriptions[key]?.complete(simulering)
    }
}

class AbetalClient(
    private val config: Config,
    private val client: HttpClient = HttpClientFactory.new(LogLevel.ALL),
    private val azure: AzureTokenProvider = AzureTokenProvider(config.azure)
) {
    suspend fun utbetaling(uid: UtbetalingId): Utbetaling {
        val response = client.get("${config.abetal.host}/api/utbetalinger${uid.id}") {
            bearerAuth(azure.getClientCredentialsToken(config.abetal.scope).access_token)
            contentType(ContentType.Application.Json)
        }
        return response.body()
    }
}

fun Route.simulerBlocking(dpUtbetalingerProducer: KafkaProducer<String, DpUtbetaling>) {

    route("/api/simulering/v3") {
        post {
            val key = call.request.headers["Transaction-ID"] ?: UUID.randomUUID().toString()

            val fagsystem = when (val name = call.client().name) {
                "azure-token-generator" -> Fagsystem.valueOf(call.request.headers["fagsystem"] ?: badRequest("header fagystem must be specified when using azure-token-generator"))
                "helved-performance" -> Fagsystem.valueOf(call.request.headers["fagsystem"] ?: badRequest("header fagystem must be specified when using azure-token-generator"))
                "tilleggsstonader-sak" -> Fagsystem.TILLEGGSSTØNADER
                "tiltakspenger-saksbehandling-api" -> Fagsystem.TILTAKSPENGER
                else -> forbidden(msg = "mangler mapping mellom appname ($name) og fagsystem-enum", doc = "kom_i_gang")
            }

            suspend fun simulerDagpenger() {
                val dto = call.receive<DpUtbetaling>()
                val deferred = SimuleringSubscriptions.subscribe(key)
                dpUtbetalingerProducer.send(key, dto)
                withTimeoutOrNull(30.seconds) { 
                    call.respond(deferred.await())
                } ?: call.respond(HttpStatusCode.RequestTimeout)
                SimuleringSubscriptions.unsubscribe(key)
            }

            when (fagsystem) {
                Fagsystem.DAGPENGER -> simulerDagpenger()
                else -> notFound("simulering/v3 for $fagsystem is not implemented yet")
            }
        }
    }
}
