package utsjekk.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import libs.kafka.Streams
import models.*
import models.Simulering
import utsjekk.*
import utsjekk.iverksetting.IverksettingService
import utsjekk.simulering.*
import utsjekk.utbetaling.Utbetaling
import utsjekk.utbetaling.UtbetalingApi
import utsjekk.utbetaling.UtbetalingId
import utsjekk.utbetaling.UtbetalingService
import java.util.*
import kotlin.time.Duration.Companion.seconds

class SimuleringRoutes(
    config: Config,
    kafka: Streams,
    iverksettingService: IverksettingService,
    private val utbetalingService: UtbetalingService,
) : AutoCloseable {
    private val validatorV2: SimuleringService = SimuleringService(iverksettingService)
    private val simuleringClient = SimuleringClient(config)
    private val utbetalingerSimuleringService = SimuleringUtbetalingService(simuleringClient)

    private val dryrunTsStore = kafka.getStore(Stores.dryrunTs)
    private val dryrunDpStore = kafka.getStore(Stores.dryrunDp)

    private val aapProducer = kafka.createProducer(config.kafka, Topics.utbetalingAap)
    private val dpProducer = kafka.createProducer(config.kafka, Topics.utbetalingDp)
    private val tpProducer = kafka.createProducer(config.kafka, Topics.utbetalingTp)
    private val tsProducer = kafka.createProducer(config.kafka, Topics.utbetalingTs)

    override fun close() {
        aapProducer.close()
        dpProducer.close()
        tpProducer.close()
        tsProducer.close()
    }

    fun aap(route: Route) {
        route.route("/utbetalinger/{uid}/simuler") {
            post {
                val uid = call.parameters["uid"]
                    ?.let(::uuid)
                    ?.let(::UtbetalingId)
                    ?: badRequest("Mangler path parameter 'uid'")

                val dto = call.receive<UtbetalingApi>().also { it.validate() }
                val domain = Utbetaling.from(dto)
                val token = call.getTokenType() ?: unauthorized("Mangler claim, enten azp_name eller NAVident")

                val response = utbetalingerSimuleringService.simuler(uid, domain, token)

                call.respond(HttpStatusCode.OK, response)
            }
            delete {
                val uid = call.parameters["uid"]
                    ?.let(::uuid)
                    ?.let(::UtbetalingId)
                    ?: badRequest("Mangler path parameter 'uid'")

                val dto = call.receive<UtbetalingApi>().also { it.validate() }
                val token = call.getTokenType() ?: unauthorized("Mangler claim, enten azp_name eller NAVident")
                val existing = utbetalingService.lastOrNull(uid) ?: notFound("Fant ikke utbetaling med uid ${uid.id}")
                val domain = Utbetaling.from(dto, existing.lastPeriodeId)
                val response = utbetalingerSimuleringService.simulerDelete(uid, domain, token)
                call.respond(HttpStatusCode.OK, response)
            }
        }
    }

    fun utsjekk(route: Route) {
        route.route("/api/simulering/v2") {
            post {
                val fagsystem = call.fagsystem()
                val dto = call.receive<api.SimuleringRequest>()
                val simulering = domain.Simulering.from(dto, fagsystem)
                validatorV2.valider(simulering)

                val token = if (call.hasClaim("NAVident")) {
                    TokenType.Obo(call.request.authorization()?.replace("Bearer ", "") ?: unauthorized("Mangler auth header"))
                } else if (call.hasClaim("azp_name")) {
                    TokenType.Client(call.request.authorization()?.replace("Bearer ", "") ?: unauthorized("Mangler auth header"))
                } else {
                    unauthorized("Mangler claims")
                }

                when (val res = simuleringClient.hentSimuleringsresultatMedOppsummering(simulering, token)) {
                    null -> call.respond(HttpStatusCode.NoContent)
                    else -> call.respond(res)
                }
            }
        }
    }

    fun abetal(route: Route) {
        route.route("/api/simulering/v3") {
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
                    dpProducer.send(transactionId, dto)

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

                    aapProducer.send(transactionId, dto)

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
                    tsProducer.send(transactionId, dto)

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

                    dtos.forEach { dto -> tpProducer.send(transactionId, dto) }

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
}

