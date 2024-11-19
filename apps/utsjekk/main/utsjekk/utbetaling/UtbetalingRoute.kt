package utsjekk.utbetaling

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import java.util.UUID
import utsjekk.badRequest
import utsjekk.notFound

fun Route.utbetalingRoute() {
    route("/utbetalinger") { 
        post {
            val dto = call.receive<UtbetalingApi>().also { it.validate() }
            val domain = Utbetaling.from(dto)
            val uid = UtbetalingService.create(domain)
            call.respond(HttpStatusCode.Created, uid.id)
        }

        get("/{id}") {
            val uid = call.parameters["id"]
                ?.let(UUID::fromString) // can fail
                ?.let(::UtbetalingId)
                ?: badRequest(msg = "missing path param", field = "id")

            DatabaseFake.findOrNull(uid)
                ?.let(UtbetalingApi::from)
                ?.let { call.respond(it) }
                ?: notFound(msg = "utbetaling", field = "id")
        }
    }
}

