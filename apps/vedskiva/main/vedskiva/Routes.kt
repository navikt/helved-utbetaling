@file:UseSerializers(libs.kotlinx.LocalDateSerializer::class, libs.kotlinx.LocalDateTimeSerializer::class, AvstemmingsdataSerializer::class)

package vedskiva

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.transaction
import libs.utils.appLog
import libs.utils.erHelligdag
import libs.utils.forrigeVirkedag
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.routing.bind
import org.http4k.routing.routes
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
data class AvstemmingRequest(
    val today: LocalDate,
    val fom: LocalDateTime,
    val tom: LocalDateTime,
)

@Serializable
data class DateBody(
    val date: LocalDate,
)

internal val dateBodyLens = VedskivaJson.autoBody<DateBody>().toLens()
internal val avstemmingRequestLens = VedskivaJson.autoBody<AvstemmingRequest>().toLens()
internal val avstemmingerLens = VedskivaJson.autoBody<List<Pair<String, List<Avstemmingsdata>>>>().toLens()

fun avstem(service: AvstemmingService, jdbcCtx: CoroutineDatasource) = routes(
    "/next_range" bind Method.POST to { request ->
        val today = dateBodyLens(request).date
        runBlocking(jdbcCtx + Dispatchers.IO) {
            val last: Scheduled? = transaction { Scheduled.lastOrNull() }
            val avstemFom = (last?.avstemt_tom?.plusDays(1) ?: today.forrigeVirkedag()).atStartOfDay()
            val avstemTom = today.atStartOfDay().minusNanos(1)
            Response(Status.OK).with(avstemmingRequestLens of AvstemmingRequest(today, avstemFom, avstemTom))
        }
    },
    "/avstem" bind Method.POST to { request ->
        val req = avstemmingRequestLens(request)
        appLog.info("starter avstemming for ${req.today}, mellom [${req.fom} - ${req.tom}]")

        runBlocking(jdbcCtx + Dispatchers.IO) {
            if (req.today.erHelligdag()) {
                return@runBlocking Response(Status.LOCKED).body("Today is a holiday or weekend, no avstemming")
            }

            val last: Scheduled? = transaction { Scheduled.lastOrNull() }
            if (req.today == last?.created_at) {
                return@runBlocking Response(Status.CONFLICT).body("Already avstemt today (${req.today})")
            }

            service.avstem(req.fom, req.tom)
            transaction {
                Scheduled(req.today, req.fom.toLocalDate(), req.tom.toLocalDate()).insert()
            }
            Response(Status.OK)
        }
    },
    "/avstem/dryrun" bind Method.POST to { request ->
        val req = avstemmingRequestLens(request)
        runBlocking(jdbcCtx + Dispatchers.IO) {
            Response(Status.OK).with(avstemmingerLens of service.avstemminger(req.fom, req.tom))
        }
    }
)

fun actuatorRoutes(metrics: PrometheusMeterRegistry) = routes(
    "/actuator/metric" bind Method.GET to {
        Response(Status.OK).body(metrics.scrape())
    },
    "/actuator/health" bind Method.GET to {
        Response(Status.OK)
    }
)
