package utsjekk.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import libs.kafka.Streams
import libs.jdbc.concurrency.CoroutineDatasource
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Bypass access for our test clients */
private val TEST_CLIENTS = setOf("azure-token-generator", "snickerboa")

/** OS/UR got 2 min timeout before rolling back a failed simulering */
private val DRYRUN_TIMEOUT = 120.seconds

data class DryrunTimeoutBody(
    val reason: String,
    val transactionId: String,
    val elapsedMs: Long,
    val msg: String,
)

class SimuleringRoutes(
    config: Config,
    kafka: Streams,
    private val metrics: Metrics,
    iverksettingService: IverksettingService,
    private val utbetalingService: UtbetalingService,
    jdbcCtx: CoroutineDatasource,
    private val dryrunTimeout: Duration = DRYRUN_TIMEOUT,
) : AutoCloseable {
    private val validatorV2: SimuleringService = SimuleringService(iverksettingService, jdbcCtx)
    private val simuleringClient = SimuleringClient(config, jdbcCtx)
    private val utbetalingerSimuleringService = SimuleringUtbetalingService(simuleringClient, jdbcCtx)

    private val dryrunAapStore = kafka.getStore(Stores.dryrunAap)
    private val dryrunDpStore = kafka.getStore(Stores.dryrunDp)
    private val dryrunTpStore = kafka.getStore(Stores.dryrunTp)
    private val dryrunTsStore = kafka.getStore(Stores.dryrunTs)

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
                measureDryrun(DryrunEndpoint.V1) {
                    val uid = call.parameters["uid"]
                        ?.let(::uuid)
                        ?.let(::UtbetalingId)
                        ?: badRequest("Mangler path parameter 'uid'")

                    val dto = call.receive<UtbetalingApi>().also { it.validate() }
                    val domain = Utbetaling.from(dto)
                    val token = call.getTokenType() ?: unauthorized("Mangler claim, enten azp_name eller NAVident")

                    val response = utbetalingerSimuleringService.simuler(uid, domain, token)

                    call.respond(HttpStatusCode.OK, response)
                    DryrunResult.OK
                }
            }
            delete {
                measureDryrun(DryrunEndpoint.V1) {
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
                    DryrunResult.OK
                }
            }
        }
    }

    fun utsjekk(route: Route) {
        route.route("/api/simulering/v2") {
            post {
                measureDryrun(DryrunEndpoint.V2) {
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

                    DryrunResult.OK
                }
            }
        }
    }

    fun abetal(route: Route) {
        route.route("/api/simulering/v3") {
            post {
                measureDryrun(DryrunEndpoint.V3) {
                    val transactionId = call.transactionId()

                    val fagsystem = when (val name = call.client().name) {
                        "azure-token-generator" -> {
                            val fagsystem = call.request.headers["fagsystem"] ?: badRequest("header fagystem must be specified when using azure-token-generator")
                            try {
                                Fagsystem.valueOf(fagsystem)
                            } catch (e: Exception) {
                                val doubleDecoded = String(fagsystem.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                                Fagsystem.valueOf(doubleDecoded)
                            }
                        }
                        "snickerboa" -> {
                            val fagsystem = call.request.headers["fagsystem"] ?: badRequest("header fagystem must be specified when using azure-token-generator")
                            try {
                                Fagsystem.valueOf(fagsystem)
                            } catch (e: Exception) {
                                val doubleDecoded = String(fagsystem.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                                Fagsystem.valueOf(doubleDecoded)
                            }
                        }
                        "tilleggsstonader-sak" -> Fagsystem.TILLEGGSSTØNADER
                        "tiltakspenger-saksbehandling-api" -> Fagsystem.TILTAKSPENGER
                        else -> forbidden(msg = "mangler mapping mellom appname ($name) og fagsystem-enum", doc = "kom_i_gang")
                    }

                    when (fagsystem) {
                        Fagsystem.DAGPENGER -> dryrunDagpenger(call, transactionId)
                        Fagsystem.AAP -> dryrunAap(call, transactionId)
                        Fagsystem.TILLEGGSSTØNADER -> dryrunTilleggsstønader(call, transactionId)
                        Fagsystem.TILTAKSPENGER -> dryrunTiltakspenger(call, transactionId)
                        else -> notFound("simulering/v3 for $fagsystem is not implemented yet")
                    }
                }
            }
        }
    }

    fun dryrun(route: Route) {
        route.route("/api/dryrun") {
            post("/aap") {
                measureDryrun(DryrunEndpoint.V3) {
                    requireClient(call, "utbetal")
                    val transactionId = call.requiredTransactionId()
                    dryrunAap(call, transactionId)
                }
            }
            post("/dagpenger") {
                measureDryrun(DryrunEndpoint.V3) {
                    requireClient(call, "dp-mellom-barken-og-veden")
                    val transactionId = call.requiredTransactionId()
                    dryrunDagpenger(call, transactionId)
                }
            }
            post("/tilleggsstonader") {
                measureDryrun(DryrunEndpoint.V3) {
                    requireClient(call, "tilleggsstonader-sak")
                    val transactionId = call.requiredTransactionId()
                    dryrunTilleggsstønader(call, transactionId)
                }
            }
            post("/tiltakspenger") {
                measureDryrun(DryrunEndpoint.V3) {
                    requireClient(call, "tiltakspenger-saksbehandling-api")
                    val transactionId = call.requiredTransactionId()
                    dryrunTiltakspenger(call, transactionId)
                }
            }
        }
    }

    private suspend fun dryrunDagpenger(call: RoutingCall, transactionId: String): DryrunResult {
        val dto = call.receive<DpUtbetaling>().copy(dryrun = true)
        dpProducer.send(transactionId, dto)

        val result = withTimeoutOrNull(dryrunTimeout) {
            while (true) {
                val simResult = dryrunDpStore.getOrNull(transactionId)
                if (simResult != null) {
                    return@withTimeoutOrNull simResult
                }
                delay(500)
            }
        }

        when (result) {
            is models.v1.Simulering -> {
                call.respond(result)
                return DryrunResult.OK
            }
            is models.v2.Simulering -> {
                call.respond(result)
                return DryrunResult.OK
            }
            is models.Info -> {
                when (result.status) {
                    Info.Status.OK_UTEN_ENDRING -> {
                        call.respond(HttpStatusCode.Found, result)
                        return DryrunResult.OK
                    }
                }
            }
            null -> {
                call.respond(HttpStatusCode.RequestTimeout, dryrunTimeoutBody(transactionId, dryrunTimeout))
                return DryrunResult.TIMEOUT
            }
            else -> {
                call.respond(HttpStatusCode.InternalServerError)
                return DryrunResult.ERROR
            }
        }
    }

    private suspend fun dryrunAap(call: RoutingCall, transactionId: String): DryrunResult {
        val dto = call.receive<AapUtbetaling>().copy(dryrun = true)
        aapProducer.send(transactionId, dto)

        val result = withTimeoutOrNull(dryrunTimeout) {
            while (true) {
                val simResult = dryrunAapStore.getOrNull(transactionId)
                if (simResult != null) {
                    return@withTimeoutOrNull simResult
                }
                delay(500)
            }
        }

        when (result) {
            is models.v1.Simulering -> {
                call.respond(result)
                return DryrunResult.OK
            }
            is models.v2.Simulering -> {
                call.respond(result)
                return DryrunResult.OK
            }
            is models.Info -> {
                when (result.status) {
                    Info.Status.OK_UTEN_ENDRING -> {
                        call.respond(HttpStatusCode.Found, result)
                        return DryrunResult.OK
                    }
                }
            }
            null -> {
                call.respond(HttpStatusCode.RequestTimeout, dryrunTimeoutBody(transactionId, dryrunTimeout))
                return DryrunResult.TIMEOUT
            }
            else -> {
                call.respond(HttpStatusCode.InternalServerError)
                return DryrunResult.ERROR
            }
        }
    }

    private suspend fun dryrunTilleggsstønader(call: RoutingCall, transactionId: String): DryrunResult {
        val dto = call.receive<TsDto>().copy(dryrun = true)
        tsProducer.send(transactionId, dto)

        val result = withTimeoutOrNull(dryrunTimeout) {
            while (true) {
                val simResult = dryrunTsStore.getOrNull(transactionId)
                if (simResult != null) {
                    return@withTimeoutOrNull simResult
                }
                delay(500)
            }
        }

        when (result) {
            is models.v1.Simulering -> {
                call.respond(result)
                return DryrunResult.OK
            }
            is models.v2.Simulering -> {
                call.respond(result)
                return DryrunResult.OK
            }
            is models.Info -> {
                when (result.status) {
                    Info.Status.OK_UTEN_ENDRING -> {
                        call.respond(HttpStatusCode.Found, result)
                        return DryrunResult.OK
                    }
                }
            }
            null -> {
                call.respond(HttpStatusCode.RequestTimeout, dryrunTimeoutBody(transactionId, dryrunTimeout))
                return DryrunResult.TIMEOUT
            }
            else -> {
                call.respond(HttpStatusCode.InternalServerError)
                return DryrunResult.ERROR
            }
        }
    }

    private suspend fun dryrunTiltakspenger(call: RoutingCall, transactionId: String): DryrunResult {
        val dto = call.receive<TpUtbetaling>().copy(dryrun = true)
        tpProducer.send(transactionId, dto)

        val result = withTimeoutOrNull(dryrunTimeout) {
            while (true) {
                val simResult = dryrunTpStore.getOrNull(transactionId)
                if (simResult != null) {
                    return@withTimeoutOrNull simResult
                }
                delay(500)
            }
        }

        when (result) {
            is models.v1.Simulering -> {
                call.respond(result)
                return DryrunResult.OK
            }
            is models.v2.Simulering -> {
                call.respond(result)
                return DryrunResult.OK
            }
            is models.Info -> {
                when (result.status) {
                    Info.Status.OK_UTEN_ENDRING -> {
                        call.respond(HttpStatusCode.Found, result)
                        return DryrunResult.OK
                    }
                }
            }
            null -> {
                call.respond(HttpStatusCode.RequestTimeout, dryrunTimeoutBody(transactionId, dryrunTimeout))
                return DryrunResult.TIMEOUT
            }
            else -> {
                call.respond(HttpStatusCode.InternalServerError)
                return DryrunResult.ERROR
            }
        }
    }

    private suspend fun measureDryrun(endpoint: DryrunEndpoint, block: suspend () -> DryrunResult) {
        val sample = metrics.startDryrunTimer()
        try {
            metrics.dryrun(endpoint, block())
        } catch (e: Exception) {
            metrics.dryrun(endpoint, DryrunResult.ERROR)
            throw e
        } finally {
            metrics.stopDryrunTimer(endpoint, sample)
        }
    }

}

private fun RoutingCall.transactionId(): String =
    request.headers["Transaction-ID"] ?: UUID.randomUUID().toString()

private fun RoutingCall.requiredTransactionId(): String =
    request.headers["Transaction-ID"] ?: notFound("Mangler header Transaction-ID")

private fun dryrunTimeoutBody(transactionId: String, timeout: kotlin.time.Duration) = DryrunTimeoutBody(
    reason = "timeout",
    transactionId = transactionId,
    elapsedMs = timeout.inWholeMilliseconds,
    msg = "Dryrun did not complete within 120s — try again or check abetal status",
)

private fun requireClient(call: RoutingCall, expectedAppName: String) {
    val name = call.client().name
    if (name in TEST_CLIENTS) return
    if (name != expectedAppName) {
        forbidden(msg = "$name har ikke tilgang til dette endepunktet (forventet $expectedAppName)")
    }
}
