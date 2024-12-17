package oppdrag.routing

import io.ktor.server.routing.*
import oppdrag.utbetaling.*
import java.util.UUID
import io.ktor.server.request.*

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
