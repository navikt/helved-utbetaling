package utsjekk.utbetaling

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.authorization
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import libs.utils.onFailure
import utsjekk.badRequest
import utsjekk.conflict
import utsjekk.internalServerError
import utsjekk.notFound
import utsjekk.unauthorized
import utsjekk.utbetaling.simulering.SimuleringService
import java.util.UUID

// TODO: valider at stønad (enum) tilhører AZP (claims)
fun Route.utbetalingRoute(
    simuleringService: SimuleringService,
    utbetalingService: UtbetalingService,
) {

    route("/utbetalinger/{uid}") {

        post {
            val uid = call.parameters["uid"]
                ?.let(::uuid)
                ?.let(::UtbetalingId)
                ?: badRequest(msg = "missing path param", field = "uid")

            val dto = call.receive<UtbetalingApi>().also { it.validate() }
            val domain = Utbetaling.from(dto)

            utbetalingService.create(uid, domain).onFailure {
                when (it) {
                    DatabaseError.Conflict -> conflict("utbetaling already exists", "uid")
                    DatabaseError.Unknown -> internalServerError("unknown database error")
                }
            }
            call.response.headers.append(HttpHeaders.Location, "/utbetalinger/${uid.id}")
            call.respond(HttpStatusCode.Created)
        }

        get {
            val uid = call.parameters["uid"]
                ?.let(::uuid)
                ?.let(::UtbetalingId)
                ?: badRequest(msg = "missing path param", field = "uid")

            val dto = utbetalingService.read(uid)
                ?.let(UtbetalingApi::from)
                ?: notFound(msg = "Fant ikke utbetaling", field = "uid")

            call.respond(HttpStatusCode.OK, dto)
        }

        get("/status") {
            val uid = call.parameters["uid"]
                ?.let(::uuid)
                ?.let(::UtbetalingId)
                ?: badRequest(msg = "missing path param", field = "uid")

            val dto = utbetalingService.status(uid)

            call.respond(HttpStatusCode.OK, dto)
        }

        put {
            val uid = call.parameters["uid"]
                ?.let(::uuid)
                ?.let(::UtbetalingId)
                ?: badRequest(msg = "missing path param", field = "uid")

            val dto = call.receive<UtbetalingApi>().also { it.validate() }
            val existing = utbetalingService.lastOrNull(uid) ?: notFound("utbetaling $uid")
            val domain = Utbetaling.from(dto, existing.lastPeriodeId)

            utbetalingService.update(uid, domain).onFailure {
                when (it) {
                    DatabaseError.Conflict -> conflict("utbetaling already exists", "uid")
                    DatabaseError.Unknown -> internalServerError("unknown database error")
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        delete {
            val uid = call.parameters["uid"]
                ?.let(::uuid)
                ?.let(::UtbetalingId)
                ?: badRequest(msg = "missing path param", field = "uid")

            val dto = call.receive<UtbetalingApi>().also { it.validate() }
            val existing = utbetalingService.lastOrNull(uid) ?: notFound("utbetaling $uid")
            val domain = Utbetaling.from(dto, existing.lastPeriodeId)

            utbetalingService.delete(uid, domain).onFailure {
                when (it) {
                    DatabaseError.Conflict -> conflict("utbetaling already exists", "uid")
                    DatabaseError.Unknown -> internalServerError("unknown database error")
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        route("/simuler") {
            post {
                val uid = call.parameters["uid"]
                    ?.let(::uuid)
                    ?.let(::UtbetalingId)
                    ?: badRequest(msg = "missing path param", field = "uid")

                val dto = call.receive<UtbetalingApi>().also { it.validate() }
                val domain = Utbetaling.from(dto)
                val token = call.request.authorization()?.replace("Bearer ", "") ?: unauthorized("auth header missing")

                val response = simuleringService.simuler(uid, domain, token)

                call.respond(HttpStatusCode.OK, response)
            }
            delete {
                val uid = call.parameters["uid"]
                    ?.let(::uuid)
                    ?.let(::UtbetalingId)
                    ?: badRequest(msg = "missing path param", field = "uid")

                val dto = call.receive<UtbetalingApi>().also { it.validate() }
                val token = call.request.authorization()?.replace("Bearer ", "") ?: unauthorized("auth header missing")
                val existing = utbetalingService.lastOrNull(uid) ?: notFound("utbetaling $uid")
                val domain = Utbetaling.from(dto, existing.lastPeriodeId)
                val response = simuleringService.simulerDelete(uid, domain, token)
                call.respond(HttpStatusCode.OK, response)
            }
        }

    }
}

private fun uuid(str: String): UUID {
    return try {
        UUID.fromString(str)
    } catch (e: Exception) {
        badRequest(msg = "path param must be UUIDv4", field = "uid")
    }
}

