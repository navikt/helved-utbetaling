@file:UseSerializers(libs.kotlinx.LocalDateSerializer::class, libs.kotlinx.LocalDateTimeSerializer::class, AvstemmingsdataSerializer::class)

package vedskiva

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.transaction
import libs.utils.appLog
import models.erHelligdag
import models.forrigeVirkedag
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

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

fun Route.avstem(service: AvstemmingService, jdbcCtx: CoroutineDatasource) {
    route("/api") {
        post("/next_range") {
            val today = call.receive<DateBody>().date
            withContext(jdbcCtx + Dispatchers.IO) {
                val last: Scheduled? = transaction { Scheduled.lastOrNull() }
                val avstemFom = (last?.avstemt_tom?.plusDays(1) ?: today.forrigeVirkedag()).atStartOfDay()
                val avstemTom = today.atStartOfDay().minusNanos(1)
                call.respond(AvstemmingRequest(today, avstemFom, avstemTom))
            }
        }

        route("/avstem") {
            post {
                val req = call.receive<AvstemmingRequest>()
                appLog.info("starter avstemming for ${req.today}, mellom [${req.fom} - ${req.tom}]")

                withContext(jdbcCtx + Dispatchers.IO) {
                    if (req.today.erHelligdag()) {
                        appLog.info("Today is a holiday or weekend, no avstemming")
                        call.respond(HttpStatusCode.Locked, "Today is a holiday or weekend, no avstemming")
                        return@withContext
                    }

                    val last: Scheduled? = transaction { Scheduled.lastOrNull() }

                    if (req.today == last?.created_at) {
                        appLog.info("Already avstemt today (${req.today})")
                        call.respond(HttpStatusCode.Conflict, "Already avstemt today (${req.today})")
                        return@withContext
                    }

                    val avstemminger = service.generate(req.fom, req.tom)

                    avstemminger.forEach { (fagområde, messages) ->
                        messages.forEach { message -> 
                            // FIXME: hvis forrige iter i forEach gikk bra, men neste feiler. Så har vi allerede sendt ut disse
                            // Hvordan kan vi gjøre alle forEach (fagområde, daos) atomisk? 
                            service.producer.send(UUID.randomUUID().toString(), message, 0)
                        }
                        appLog.info("Avstemming for $fagområde completed with avstemmingId: ${messages.first().aksjon.avleverendeAvstemmingId}")
                    }

                    transaction {
                        Scheduled(req.today, req.fom.toLocalDate(), req.tom.toLocalDate()).insert()
                    }

                    call.respond(HttpStatusCode.OK)
                }
            }

            post("/dryrun") {
                withContext(jdbcCtx + Dispatchers.IO) {
                    val req = call.receive<AvstemmingRequest>()
                    val avstemminger = service.generate(req.fom, req.tom)
                    call.respond(avstemminger)
                }
            }
        }

        route("/avstem2") {
            query { req: AvstemmingRequest ->
                appLog.info("starter avstemming for ${req.today}, mellom [${req.fom} - ${req.tom}]")

                withContext(jdbcCtx + Dispatchers.IO) {
                    if (req.today.erHelligdag()) {
                        call.respond(HttpStatusCode.Locked, "Today is a holiday or weekend, no avstemming")
                        return@withContext
                    }

                    val last: Scheduled? = transaction { Scheduled.lastOrNull() }

                    if (req.today == last?.created_at) {
                        call.respond(HttpStatusCode.Conflict, "Already avstemt today (${req.today})")
                        return@withContext
                    }

                    val avstemminger = service.generate2(req.fom, req.tom)

                    avstemminger.forEach { (fagområde, messages) ->
                        messages.forEach { message ->
                            // FIXME: hvis forrige iter i forEach gikk bra, men neste feiler. Så har vi allerede sendt ut disse
                            // Hvordan kan vi gjøre alle forEach (fagområde, daos) atomisk?
                            service.producer.send(UUID.randomUUID().toString(), message, 0)
                        }
                        appLog.info("Avstemming for $fagområde completed with avstemmingId: ${messages.first().aksjon.avleverendeAvstemmingId}")
                    }

                    transaction {
                        Scheduled(req.today, req.fom.toLocalDate(), req.tom.toLocalDate()).insert()
                    }

                    call.respond(HttpStatusCode.OK)
                }
            }

            query("/dryrun") {
                val req = call.receive<AvstemmingRequest>()
                withContext(jdbcCtx + Dispatchers.IO) {
                    val avstemminger = service.generate2(req.fom, req.tom)
                    call.respond(avstemminger)
                }
            }
        }
    }
}

