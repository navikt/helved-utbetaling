package oppdrag.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withContext
import libs.postgres.Postgres
import libs.utils.appLog
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.oppdrag.GrensesnittavstemmingRequest
import oppdrag.grensesnittavstemming.GrensesnittavstemmingService

fun Route.avstemmingRoutes(
    service: GrensesnittavstemmingService,
) {
    route("/grensesnittavstemming") {
        post {
            withContext(Postgres.context) {
                val request = call.receive<GrensesnittavstemmingRequest>()
                appLog.info("Grensesnittavstemming: Kjører for ${request.fagsystem}-oppdrag fra ${request.fra} til ${request.til}")

                runCatching {
                    service.utførGrensesnittavstemming(
                        fagsystem = request.fagsystem,
                        fra = request.fra,
                        til = request.til,
                    )
                }.onSuccess {
                    call.respond(HttpStatusCode.Created)
                }.onFailure {
                    appLog.error("Feil ved grensesnittavstemming")
                    secureLog.error("Feil ved grensesnittavstemming", it)
                    call.respond(HttpStatusCode.InternalServerError, "Grensesnittavstemming feilet")
                }
            }
        }
    }
}
