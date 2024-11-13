package utsjekk.iverksetting.v3

import utsjekk.iverksetting.v3.UtbetalingsoppdragService
import utsjekk.iverksetting.v3.Utbetaling
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import java.util.UUID

fun Route.utbetalingRoute() {
    post("/utbetalinger") {
        val dto = call.receive<UtbetalingApi>()
        dto.validate()
        val domain = Utbetaling.from(dto)
        val id = DatabaseFake.save(domain)
        call.respond(HttpStatusCode.Created, id)
    }

    get("/utbetalinger/{id}") {
        val id = call.parameters["id"]
            ?.let { UtbetalingId(UUID.fromString(it)) } 
            ?: error("bad request")

        DatabaseFake.findOrNull(id)
            ?.let { UtbetalingApi.from(it) }
            ?.let { call.respond(it) }
            ?: call.respond(HttpStatusCode.NotFound)
    }

}

