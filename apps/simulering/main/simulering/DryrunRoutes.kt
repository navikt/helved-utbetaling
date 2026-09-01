@file:Suppress("DEPRECATION")

package simulering

import libs.auth.Jwt
import libs.kafka.StateStore
import libs.kafka.Streams
import models.*
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.RequestContextLens
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import java.util.*

/** OS/UR got 2 min timeout before rolling back a failed simulering */
private const val DRYRUN_TIMEOUT_MS = 120_000L
private const val POLL_INTERVAL_MS = 500L

fun dryrunRoutes(
    kafka: Streams,
    config: Config,
    claimsLens: RequestContextLens<Jwt.Claims>,
): RoutingHttpHandler {
    val aapProducer = kafka.createProducer(config.kafka, Topics.utbetalingAap)
    val dpProducer = kafka.createProducer(config.kafka, Topics.utbetalingDp)
    val tpProducer = kafka.createProducer(config.kafka, Topics.utbetalingTp)
    val tsProducer = kafka.createProducer(config.kafka, Topics.utbetalingTs)

    val dryrunAapStore = kafka.getStore(Stores.dryrunAap)
    val dryrunDpStore = kafka.getStore(Stores.dryrunDp)
    val dryrunTpStore = kafka.getStore(Stores.dryrunTp)
    val dryrunTsStore = kafka.getStore(Stores.dryrunTs)

    val aapBody = KotlinxJson.autoBody<AapUtbetaling>().toLens()
    val dpBody = KotlinxJson.autoBody<DpUtbetaling>().toLens()
    val tpBody = KotlinxJson.autoBody<TpUtbetaling>().toLens()
    val tsBody = KotlinxJson.autoBody<TsDto>().toLens()

    fun dryrunAap(req: Request, transactionId: String): Response {
        val dto = aapBody(req).copy(dryrun = true)
        aapProducer.send(transactionId, dto)
        return respondFromStore(dryrunAapStore, transactionId)
    }

    fun dryrunDagpenger(req: Request, transactionId: String): Response {
        val dto = dpBody(req).copy(dryrun = true)
        dpProducer.send(transactionId, dto)
        return respondFromStore(dryrunDpStore, transactionId)
    }

    fun dryrunTilleggsstønader(req: Request, transactionId: String): Response {
        val dto = tsBody(req).copy(dryrun = true)
        tsProducer.send(transactionId, dto)
        return respondFromStore(dryrunTsStore, transactionId)
    }

    fun dryrunTiltakspenger(req: Request, transactionId: String): Response {
        val dto = tpBody(req).copy(dryrun = true)
        tpProducer.send(transactionId, dto)
        return respondFromStore(dryrunTpStore, transactionId)
    }

    return routes(
        "/api/simulering" bind Method.POST to { req ->
            val claims = claimsLens(req)
            val transactionId = req.transactionId()

            val fs = when (System.getenv("ENV")) {
                "prod" -> fagsystem(claims.clientName())
                else -> req.header("fagsystem")?.let(Fagsystem::fromFagområde) ?: fagsystem(claims.clientName())
            }

            when (fs) {
                Fagsystem.DAGPENGER -> dryrunDagpenger(req, transactionId)
                Fagsystem.AAP -> dryrunAap(req, transactionId)
                Fagsystem.TILLEGGSSTØNADER -> dryrunTilleggsstønader(req, transactionId)
                Fagsystem.TILTAKSPENGER -> dryrunTiltakspenger(req, transactionId)
                else -> Response(Status.NOT_FOUND).body("simulering for $fs is not implemented yet")
            }
        },
    )
}

private fun fagsystem(fs: String): Fagsystem =
    when (fs) {
        "azure-token-generator" -> Fagsystem.AAP
        "helved-peisen" -> Fagsystem.TILLEGGSSTØNADER
        "snickerboa" -> Fagsystem.DAGPENGER
        "utbetal" -> Fagsystem.AAP
        "dp-mellom-barken-og-veden" -> Fagsystem.DAGPENGER
        "tilleggsstonader-sak" -> Fagsystem.TILLEGGSSTØNADER
        "tiltakspenger-saksbehandling-api" -> Fagsystem.TILTAKSPENGER
        else ->
            forbidden(
                msg = "mangler mapping mellom appname ($fs) og fagsystem-enum",
                doc = "kom_i_gang",
            )
    }

private fun respondFromStore(store: StateStore<String, Simulering>, key: String): Response {
    val result = pollStore(store, key)
    return when (result) {
        is Info -> respondSimulering(result, Status.FOUND)
        is Simulering -> respondSimulering(result, Status.OK)
        null -> Response(Status.REQUEST_TIMEOUT)
    }
}

private fun pollStore(store: StateStore<String, Simulering>, key: String): Simulering? {
    val deadline = System.currentTimeMillis() + DRYRUN_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
        val result = store.getOrNull(key)
        if (result != null) return result
        Thread.sleep(POLL_INTERVAL_MS)
    }
    return null
}

private fun respondSimulering(simulering: Simulering, status: Status): Response {
    val json = libs.kotlinx.KotlinxJson.encodeToString(Simulering.serializer(), simulering)
    return Response(status)
        .header("Content-Type", "application/json")
        .body(json)
}

private fun Request.transactionId(): String =
    header("Transaction-ID") ?: UUID.randomUUID().toString()
