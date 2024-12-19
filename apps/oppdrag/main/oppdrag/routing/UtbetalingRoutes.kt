package oppdrag.routing

import io.ktor.server.routing.*
import io.ktor.http.*
import oppdrag.utbetaling.*
import java.util.UUID
import io.ktor.server.request.*
import io.ktor.server.response.*

fun Route.utbetalingRoutes(
    service: UtbetalingService,
) {

    route("/utbetalingsoppdrag/{uid}") {
        post {
            val uid = call.parameters["uid"]
                ?.let(::uuid)
                ?.let(::UtbetalingId)
                ?: error("missing path param uid") 

            val dto = call.receive<UtbetalingsoppdragDto>()

            service.opprettOppdrag(dto)
        }

        get("/status") {
            val uid = call.parameters["uid"]
                ?.let(::uuid)
                ?.let(::UtbetalingId)
                ?: error("missing path param uid") 


            val status = service.hentStatusForOppdrag(uid)
            if (status == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(HttpStatusCode.OK, status)
            }
        }
    }
}

private fun uuid(str: String): UUID {
    return try {
        UUID.fromString(str)
    } catch(e: Exception) {
        error("path param uid must be UUIDv4")
    }
}
