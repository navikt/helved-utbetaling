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
            UtbetalingService.create(uid, domain) // TODO: kan feile med sql unique constraint violation
            call.response.headers.append(HttpHeaders.Location, "/utbetalinger/${uid.id}")
            call.respond(HttpStatusCode.Created)
        }

        get {
            val uid = call.parameters["uid"]
                ?.let(::uuid)
                ?.let(::UtbetalingId)
                ?: badRequest(msg = "missing path param", field = "uid") 

            val dto = UtbetalingService.read(uid)
                ?.let(UtbetalingApi::from)
                ?: notFound(msg = "utbetaling", field = "uid")

            call.respond(dto)
        }

        put {
            val uid = call.parameters["uid"]
                ?.let(::uuid)
                ?.let(::UtbetalingId)
                ?: badRequest(msg = "missing path param", field = "uid") 

            val dto = call.receive<UtbetalingApi>().also { it.validate() }
            val domain = Utbetaling.from(dto)
            UtbetalingService.update(uid, domain) // TODO: return Result<T, E>
            call.respond(HttpStatusCode.OK)
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

