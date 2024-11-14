package utsjekk.iverksetting.v3

import utsjekk.iverksetting.v3.UtbetalingsoppdragService
import utsjekk.iverksetting.v3.Utbetaling
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import java.util.UUID
import utsjekk.badRequest
import utsjekk.notFound

fun Route.utbetalingRoute() {
    post("/utbetalinger") {
        val dto = call.receive<UtbetalingApi>().also { it.validate() }
        val domain = Utbetaling.from(dto)
        val uid = DatabaseFake.save(domain)
        call.respond(HttpStatusCode.Created, uid.id)
    }

    get("/utbetalinger/{id}") {
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

