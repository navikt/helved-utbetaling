package utsjekk.simulering

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
import models.locked
import utsjekk.Config
import utsjekk.client
import utsjekk.utbetaling.Utbetaling
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
            val key = call.request.headers["Transaction-ID"] ?: UUID.randomUUID().toString().also {
                println("Transaction-ID mangler: $it")
            }

            val fagsystem = when (call.client().name) {
                "azure-token-generator" -> Fagsystem.DAGPENGER
                else -> TODO("Implementer azp for ${call.client().name}")
            }

            suspend fun simulerDagpenger() {
                val dto = call.receive<DpUtbetaling>()
                val response = SimuleringSubscriptions.subscribe(key)
                dpUtbetalingerProducer.send(key, dto)
                withTimeoutOrNull(30.seconds) { call.respond(response.await()) }
                    ?: call.respond(HttpStatusCode.RequestTimeout)
                SimuleringSubscriptions.unsubscribe(key)
            }

            when (fagsystem) {
                Fagsystem.DAGPENGER -> simulerDagpenger()
                Fagsystem.AAP -> TODO()
                Fagsystem.TILTAKSPENGER -> TODO()
                Fagsystem.TILLEGGSSTØNADER -> TODO()
                Fagsystem.HISTORISK -> TODO()
            }

        }
    }
}
