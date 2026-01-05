package utsjekk.simulering

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.kafka.KafkaProducer
import libs.kafka.StateStore
import models.AapUtbetaling
import models.DpUtbetaling
import models.Fagsystem
import models.Simulering
import models.Status
import models.StatusReply
import models.TpUtbetaling
import models.TsDto
import models.TsUtbetaling
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
    private val subscriptionsV1 = ConcurrentHashMap<String, CompletableDeferred<models.v1.Simulering>>()
    private val statuses = ConcurrentHashMap<String, CompletableDeferred<StatusReply>>()
    val subscriptionEvents = Channel<String>(Channel.UNLIMITED)

    fun subscribeV1(key: String): Pair<CompletableDeferred<models.v1.Simulering>, CompletableDeferred<StatusReply>> {
        libs.utils.appLog.info("subscribe to $key")
        if (subscriptionsV1.containsKey(key)) locked("Simulering for $key pågår allerede")

        val simuleringDeferred = CompletableDeferred<models.v1.Simulering>()
        subscriptionsV1[key] = simuleringDeferred 

        val statusDeferred = CompletableDeferred<StatusReply>()
        statuses[key] = statusDeferred

        subscriptionEvents.trySend(key)
        return simuleringDeferred to statusDeferred
    }

    fun subscribe(key: String): Pair<CompletableDeferred<Simulering>, CompletableDeferred<StatusReply>> {
        libs.utils.appLog.info("subscribe to $key")
        if (subscriptions.containsKey(key)) locked("Simulering for $key pågår allerede")

        val simuleringDeferred = CompletableDeferred<Simulering>()
        subscriptions[key] = simuleringDeferred 

        val statusDeferred = CompletableDeferred<StatusReply>()
        statuses[key] = statusDeferred

        subscriptionEvents.trySend(key)
        return simuleringDeferred to statusDeferred
    }

    fun unsubscribe(key: String) {
        libs.utils.appLog.info("unsubscribe to $key")
        subscriptions[key]?.cancel()
        subscriptions.remove(key)

        subscriptionsV1[key]?.cancel()
        subscriptionsV1.remove(key)

        statuses[key]?.cancel()
        statuses.remove(key)
    }

    fun complete(key: String, simulering: Simulering) {
        libs.utils.appLog.info("received sim for $key")
        subscriptions[key]?.complete(simulering)
    }

    fun complete(key: String, simulering: models.v1.Simulering) {
        libs.utils.appLog.info("received sim for $key")
        subscriptionsV1[key]?.complete(simulering)
    }

    fun complete(key: String, status: StatusReply) {
        libs.utils.appLog.info("received status ${status.status} for $key")
        statuses[key]?.complete(status)
    }
}

fun Route.simulerBlocking(
    aapUtbetalingerProducer: KafkaProducer<String, AapUtbetaling>,
    dpUtbetalingerProducer: KafkaProducer<String, DpUtbetaling>,
    tpUtbetalingerProducer: KafkaProducer<String, TpUtbetaling>,
    tsUtbetalingerProducer: KafkaProducer<String, TsDto>,
    dryrunTsStore: StateStore<String, models.v1.Simulering>, 
    dryrunDpStore: StateStore<String, Simulering>, 
) {

    route("/api/simulering/v3") {
        post {
            val transactionId = call.request.headers["Transaction-ID"] ?: UUID.randomUUID().toString()

            val fagsystem = when (val name = call.client().name) {
                // "azure-token-generator" -> Fagsystem.valueOf(call.request.headers["fagsystem"] ?: badRequest("header fagystem must be specified when using azure-token-generator"))
                "azure-token-generator" -> {
                    val fagsystem = call.request.headers["fagsystem"] ?: badRequest("header fagystem must be specified when using azure-token-generator")
                    try {
                        Fagsystem.valueOf(fagsystem)
                    } catch (e: Exception) {
                        val doubleDecoded = String(fagsystem.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                        Fagsystem.valueOf(doubleDecoded)
                    }
                }
                "helved-performance" -> {
                    val fagsystem = call.request.headers["fagsystem"] ?: badRequest("header fagystem must be specified when using azure-token-generator")
                    try {
                        Fagsystem.valueOf(fagsystem)
                    } catch (e: Exception) {
                        val doubleDecoded = String(fagsystem.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                        Fagsystem.valueOf(doubleDecoded)
                    }
                }
                // "helved-performance" -> Fagsystem.valueOf(call.request.headers["fagsystem"] ?: badRequest("header fagystem must be specified when using azure-token-generator"))
                "tilleggsstonader-sak" -> Fagsystem.TILLEGGSSTØNADER
                "tiltakspenger-saksbehandling-api" -> Fagsystem.TILTAKSPENGER
                else -> forbidden(msg = "mangler mapping mellom appname ($name) og fagsystem-enum", doc = "kom_i_gang")
            }

            suspend fun simulerDagpenger() {
                val dto = call.receive<DpUtbetaling>().copy(dryrun = true)
                dpUtbetalingerProducer.send(transactionId, dto)

                val result = withTimeoutOrNull(120.seconds) { 
                    while(true) {
                        val simResult = dryrunDpStore.getOrNull(transactionId)
                        if (simResult != null) {
                            return@withTimeoutOrNull simResult
                        }
                        delay(500) 
                    }
                }
                when (result) {
                    is Simulering -> call.respond(result)
                    // is StatusReply -> call.respond(HttpStatusCode.BadRequest, result)
                    null -> call.respond(HttpStatusCode.RequestTimeout)
                    else -> call.respond(HttpStatusCode.InternalServerError)
                }
                SimuleringSubscriptions.unsubscribe(transactionId)
            }

            suspend fun simulerAap() {
                val dto = call.receive<AapUtbetaling>().copy(dryrun = true)
                val (sim, status) = SimuleringSubscriptions.subscribe(transactionId)

                aapUtbetalingerProducer.send(transactionId, dto)

                val result = withTimeoutOrNull(120.seconds) { 
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
                SimuleringSubscriptions.unsubscribe(transactionId)
            }

            suspend fun simulerTilleggsstønader() {
                val dto = call.receive<TsDto>().copy(dryrun = true)
                tsUtbetalingerProducer.send(transactionId, dto)

                val result = withTimeoutOrNull(120.seconds) { 
                    while(true) {
                        val simResult = dryrunTsStore.getOrNull(transactionId)
                        if (simResult != null) {
                            return@withTimeoutOrNull simResult
                        }
                        delay(500) 
                    }
                }

                when (result) {
                    is models.v1.Simulering -> call.respond(result)
                    // is StatusReply -> call.respond(HttpStatusCode.BadRequest, result)
                    null -> call.respond(HttpStatusCode.RequestTimeout)
                    else -> call.respond(HttpStatusCode.InternalServerError)
                }
            }

            suspend fun simulerTiltakspenger() {
                val dtos = call.receive<List<TpUtbetaling>>().map { it.copy(dryrun = true) }
                val (sim, status) = SimuleringSubscriptions.subscribeV1(transactionId)

                dtos.forEach { dto -> tpUtbetalingerProducer.send(transactionId, dto) }

                val result = withTimeoutOrNull(120.seconds) { 
                    select<Any?> {
                        sim.onAwait { it }
                        status.onAwait { it }
                    }
                }
                when (result) {
                    is models.v1.Simulering -> call.respond(result)
                    is StatusReply -> call.respond(HttpStatusCode.BadRequest, result)
                    null -> call.respond(HttpStatusCode.RequestTimeout)
                    else -> call.respond(HttpStatusCode.InternalServerError)
                }
                SimuleringSubscriptions.unsubscribe(transactionId)
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

