package utsjekk.simulering

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.kafka.KafkaProducer
import models.AapUtbetaling
import models.DpUtbetaling
import models.TpUtbetaling
import models.TsUtbetaling
import models.Fagsystem
import models.Simulering
import models.StatusReply
import models.UtbetalingId
import models.badRequest
import models.forbidden
import models.locked
import models.notFound
import utsjekk.Config
import utsjekk.client
import utsjekk.utbetaling.Utbetaling

object SimuleringSubscriptions {

    private val subscriptions = ConcurrentHashMap<String, CompletableDeferred<Simulering>>()
    private val statuses = ConcurrentHashMap<String, CompletableDeferred<StatusReply>>()
    val subscriptionEvents = Channel<String>(Channel.UNLIMITED)

    fun subscribe(key: String): Pair<CompletableDeferred<Simulering>, CompletableDeferred<StatusReply>> {
        if (subscriptions.containsKey(key)) locked("Simulering for $key pågår allerede")

        val simuleringDeferred = CompletableDeferred<Simulering>()
        subscriptions[key] = simuleringDeferred 

        val statusDeferred = CompletableDeferred<StatusReply>()
        statuses[key] = statusDeferred

        subscriptionEvents.trySend(key)
        return simuleringDeferred to statusDeferred
    }

    fun unsubscribe(key: String) {
        subscriptions[key]?.cancel()
        subscriptions.remove(key)
        statuses[key]?.cancel()
        statuses.remove(key)
    }

    fun complete(key: String, simulering: Simulering) {
        subscriptions[key]?.complete(simulering)
    }

    fun complete(key: String, status: StatusReply) {
        statuses[key]?.complete(status)
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

fun Route.simulerBlocking(
    aapUtbetalingerProducer: KafkaProducer<String, AapUtbetaling>,
    dpUtbetalingerProducer: KafkaProducer<String, DpUtbetaling>,
    tpUtbetalingerProducer: KafkaProducer<String, TpUtbetaling>,
    tsUtbetalingerProducer: KafkaProducer<String, TsUtbetaling>,
) {

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
                val (sim, status) = SimuleringSubscriptions.subscribe(key)

                dpUtbetalingerProducer.send(key, dto)

                val result = withTimeoutOrNull(30.seconds) { 
                    select<Any?> {
                        sim.onAwait { it }
                        status.onAwait { it }
                    }
                }
                when (result) {
                    is Simulering -> call.respond(result)
                    is StatusReply -> call.respond(HttpStatusCode.BadRequest, result)
                    null -> call.respond(HttpStatusCode.RequestTimeout)
                    else -> call.respond(HttpStatusCode.InternalServerError)
                }
                SimuleringSubscriptions.unsubscribe(key)
            }

            suspend fun simulerAap() {
                val dto = call.receive<AapUtbetaling>()
                val (sim, status) = SimuleringSubscriptions.subscribe(key)

                aapUtbetalingerProducer.send(key, dto)

                val result = withTimeoutOrNull(30.seconds) { 
                    select<Any?> {
                        sim.onAwait { it }
                        status.onAwait { it }
                    }
                }
                when (result) {
                    is Simulering -> call.respond(result)
                    is StatusReply -> call.respond(HttpStatusCode.BadRequest, result)
                    null -> call.respond(HttpStatusCode.RequestTimeout)
                    else -> call.respond(HttpStatusCode.InternalServerError)
                }
                SimuleringSubscriptions.unsubscribe(key)
            }

            suspend fun simulerTilleggsstønader() {
                val dto = call.receive<TsUtbetaling>()
                val (sim, status) = SimuleringSubscriptions.subscribe(key)

                tsUtbetalingerProducer.send(key, dto)

                val result = withTimeoutOrNull(30.seconds) { 
                    select<Any?> {
                        sim.onAwait { it }
                        status.onAwait { it }
                    }
                }
                when (result) {
                    is Simulering -> call.respond(result)
                    is StatusReply -> call.respond(HttpStatusCode.BadRequest, result)
                    null -> call.respond(HttpStatusCode.RequestTimeout)
                    else -> call.respond(HttpStatusCode.InternalServerError)
                }
                SimuleringSubscriptions.unsubscribe(key)
            }

            suspend fun simulerTiltakspenger() {
                val dto = call.receive<TpUtbetaling>()
                val (sim, status) = SimuleringSubscriptions.subscribe(key)

                tpUtbetalingerProducer.send(key, dto)

                val result = withTimeoutOrNull(30.seconds) { 
                    select<Any?> {
                        sim.onAwait { it }
                        status.onAwait { it }
                    }
                }
                when (result) {
                    is Simulering -> call.respond(result)
                    is StatusReply -> call.respond(HttpStatusCode.BadRequest, result)
                    null -> call.respond(HttpStatusCode.RequestTimeout)
                    else -> call.respond(HttpStatusCode.InternalServerError)
                }
                SimuleringSubscriptions.unsubscribe(key)
            }

            when (fagsystem) {
                Fagsystem.DAGPENGER -> simulerDagpenger()
                Fagsystem.AAP -> simulerAap()
                Fagsystem.TILLEGGSSTØNADER -> simulerTilleggsstønader() // de andre fagområdene blir utledet fra DTOen
                Fagsystem.TILTAKSPENGER -> simulerTiltakspenger()
                else -> notFound("simulering/v3 for $fagsystem is not implemented yet")
            }
        }
    }
}
