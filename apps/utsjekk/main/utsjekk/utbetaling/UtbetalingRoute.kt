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
    route("/utbetalinger/{uid}") { 
        post {
            val uid = call.parameters["uid"]
                ?.let(::uuid)
                ?.let(::UtbetalingId)
                ?: badRequest(msg = "missing path param", field = "uid") 

            val dto = call.receive<UtbetalingApi>().also { it.validate() }
            val domain = Utbetaling.from(dto)
            UtbetalingService.create(uid, domain)
            call.response.headers.append(HttpHeaders.Location, "/utbetalinger/${uid.id}")
            call.respond(HttpStatusCode.Created)
        }

        get {
            val uid = call.parameters["uid"]
                ?.let(::uuid)
                ?.let(::UtbetalingId)
                ?: badRequest(msg = "missing path param", field = "uid") 

            DatabaseFake.findOrNull(uid)
                ?.let(UtbetalingApi::from)
                ?.let { call.respond(it) }
                ?: notFound(msg = "utbetaling", field = "uid")
        }
    
    }
}

private fun uuid(str: String): UUID {
    return try {
        UUID.fromString(str)
    } catch(e: Exception) {
        badRequest(msg = "path param must be UUIDv4", field = "uid")
    }
}

