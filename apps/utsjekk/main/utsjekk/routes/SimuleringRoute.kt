package utsjekk.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import libs.kafka.KafkaProducer
import libs.kafka.StateStore
import models.*
import models.Simulering
import utsjekk.TokenType
import utsjekk.client
import utsjekk.fagsystem
import utsjekk.hasClaim
import utsjekk.simulering.*
import java.util.*
import kotlin.time.Duration.Companion.seconds

fun Route.simulering(
    validator: SimuleringValidator,
    client: SimuleringClient,
    aapUtbetalingerProducer: KafkaProducer<String, AapUtbetaling>,
    dpUtbetalingerProducer: KafkaProducer<String, DpUtbetaling>,
    tpUtbetalingerProducer: KafkaProducer<String, TpUtbetaling>,
    tsUtbetalingerProducer: KafkaProducer<String, TsDto>,
    dryrunTsStore: StateStore<String, models.v1.Simulering>, 
    dryrunDpStore: StateStore<String, Simulering>, 
) {

    route("/api/simulering/v2") {
        post {
            val fagsystem = call.fagsystem()
            val dto = call.receive<api.SimuleringRequest>()
            val simulering = utsjekk.simulering.Simulering.from(dto, fagsystem)
            validator.valider(simulering)

            val token = if (call.hasClaim("NAVident")) {
                TokenType.Obo(call.request.authorization()?.replace("Bearer ", "") ?: unauthorized("Mangler auth header"))
            } else if (call.hasClaim("azp_name")) {
                TokenType.Client(call.request.authorization()?.replace("Bearer ", "") ?: unauthorized("Mangler auth header"))
            } else {
                unauthorized("Mangler claims")
            }

            when (val res = client.hentSimuleringsresultatMedOppsummering(simulering, token)) {
                null -> call.respond(HttpStatusCode.NoContent)
                else -> call.respond(res)
            }
        }
    }


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

