package snickerboa

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import models.AapUtbetaling
import models.DpUtbetaling
import models.HistoriskUtbetaling
import models.Simulering
import models.StatusReply
import models.TpUtbetaling
import models.TsDto
import models.ValpUtbetaling
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.lens.Path
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import java.util.UUID

internal val aapLens = KotlinxJson.autoBody<AapUtbetaling>().toLens()
private val dpLens = KotlinxJson.autoBody<DpUtbetaling>().toLens()
private val tsLens = KotlinxJson.autoBody<TsDto>().toLens()
private val tpLens = KotlinxJson.autoBody<TpUtbetaling>().toLens()
private val historiskLens = KotlinxJson.autoBody<HistoriskUtbetaling>().toLens()
private val valpLens = KotlinxJson.autoBody<ValpUtbetaling>().toLens()
private val statusReplyLens = KotlinxJson.autoBody<StatusReply>().toLens()
private val simuleringLens = KotlinxJson.autoBody<Simulering>().toLens()

private val transactionIdPath = Path.map(UUID::fromString, UUID::toString).of("transaction_id")

private fun UtbetalingResponse.toHttpResponse(): Response = when (this) {
    is UtbetalingResponse.StatusResult -> Response(statusCode).with(statusReplyLens of body)
    is UtbetalingResponse.Simulering -> Response(statusCode).with(simuleringLens of body)
}

fun snickerboaRoutes(correlator: RequestReplyCorrelator): RoutingHttpHandler = routes(
    "/abetal/aap" bind Method.POST to { req ->
        val dto = aapLens(req)
        val txId = UUID.randomUUID()
        runBlocking {
            correlator.handleUtbetaling(dto.dryrun, txId) {
                correlator.producers.produceAap(it, libs.kotlinx.KotlinxJson.encodeToString(dto).toByteArray())
            }
        }.toHttpResponse()
    },

    "/abetal/dp" bind Method.POST to { req ->
        val dto = dpLens(req)
        val txId = UUID.randomUUID()
        runBlocking {
            correlator.handleUtbetaling(dto.dryrun, txId) {
                correlator.producers.produceDp(it, libs.kotlinx.KotlinxJson.encodeToString(dto).toByteArray())
            }
        }.toHttpResponse()
    },

    "/abetal/dp/{transaction_id}" bind Method.POST to { req ->
        val txId = transactionIdPath(req)
        val dto = dpLens(req)
        runBlocking {
            correlator.handleUtbetaling(dto.dryrun, txId) {
                correlator.producers.produceDp(it, libs.kotlinx.KotlinxJson.encodeToString(dto).toByteArray())
            }
        }.toHttpResponse()
    },

    "/abetal/ts" bind Method.POST to { req ->
        val dto = tsLens(req)
        val txId = UUID.randomUUID()
        runBlocking {
            correlator.handleUtbetaling(dto.dryrun, txId) {
                correlator.producers.produceTs(it, libs.kotlinx.KotlinxJson.encodeToString(dto).toByteArray())
            }
        }.toHttpResponse()
    },

    "/abetal/tp" bind Method.POST to { req ->
        val dto = tpLens(req)
        val txId = UUID.randomUUID()
        runBlocking {
            correlator.handleUtbetaling(dto.dryrun, txId) {
                correlator.producers.produceTp(it, libs.kotlinx.KotlinxJson.encodeToString(dto).toByteArray())
            }
        }.toHttpResponse()
    },

    "/abetal/historisk" bind Method.POST to { req ->
        val dto = historiskLens(req)
        val txId = UUID.randomUUID()
        runBlocking {
            correlator.handleUtbetaling(dto.dryrun, txId) {
                correlator.producers.produceHistorisk(it, libs.kotlinx.KotlinxJson.encodeToString(dto).toByteArray())
            }
        }.toHttpResponse()
    },

    "/abetal/valp" bind Method.POST to { req ->
        val dto = valpLens(req)
        val txId = UUID.randomUUID()
        runBlocking {
            correlator.handleUtbetaling(dto.dryrun, txId) {
                correlator.producers.produceValp(it, libs.kotlinx.KotlinxJson.encodeToString(dto).toByteArray())
            }
        }.toHttpResponse()
    },

    // Brukes for å teste ikke-deserialiserbare meldinger
    "/abetal/raw/{fagsystem}" bind Method.POST to { req: Request ->
        val fagsystem = req.path("fagsystem")
        if (fagsystem == null) {
            Response(Status.BAD_REQUEST).body("Fagsystem parameter is required")
        } else {
            val body = req.body.payload.array()
            val txId = UUID.randomUUID()
            runBlocking {
                correlator.handleUtbetaling(false, txId) {
                    when (fagsystem) {
                        "dp" -> correlator.producers.produceDp(it, body)
                        "ts" -> correlator.producers.produceTs(it, body)
                        "tp" -> correlator.producers.produceTp(it, body)
                        "aap" -> correlator.producers.produceAap(it, body)
                        "historisk" -> correlator.producers.produceHistorisk(it, body)
                    }
                }
            }.toHttpResponse()
        }
    },
)

fun probes(meters: PrometheusMeterRegistry): RoutingHttpHandler = routes(
    "/actuator/metric" bind Method.GET to {
        Response(Status.OK).body(meters.scrape())
    },
    "/actuator/ready" bind Method.GET to {
        Response(Status.OK)
    },
    "/actuator/live" bind Method.GET to {
        Response(Status.OK)
    },
)
