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
import io.ktor.server.routing.RoutingCall
import libs.utils.onFailure
import models.badRequest
import models.conflict
import models.internalServerError
import models.notFound
import models.unauthorized
import utsjekk.utbetaling.simulering.SimuleringService
import utsjekk.hasClaim
import utsjekk.TokenType
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
                ?: badRequest("Mangler path parameter 'uid'")

            val dto = call.receive<UtbetalingApi>().also { it.validate() }
            val domain = Utbetaling.from(dto)

            utbetalingService.create(uid, domain).onFailure {
                when (it) {
                    DatabaseError.Conflict -> conflict("Utbetaling med uid $uid finnes allerede")
                    DatabaseError.Unknown -> internalServerError("Ukjent databasefeil, helved har blitt varslet")
                }
            }
            call.response.headers.append(HttpHeaders.Location, "/utbetalinger/${uid.id}")
            call.respond(HttpStatusCode.Created)
        }

        get {
            val uid = call.parameters["uid"]
                ?.let(::uuid)
                ?.let(::UtbetalingId)
                ?: badRequest("Mangler path parameter 'uid'")

            val dto = utbetalingService.read(uid)
                ?.let(UtbetalingApi::from)
                ?: notFound("Fant ikke utbetaling med uid ${uid.id}")

            call.respond(HttpStatusCode.OK, dto)
        }

        get("/status") {
            val uid = call.parameters["uid"]
                ?.let(::uuid)
                ?.let(::UtbetalingId)
                ?: badRequest("Mangler path parameter 'uid'")

            val dto = utbetalingService.status(uid)

            call.respond(HttpStatusCode.OK, dto)
        }

        put {
            val uid = call.parameters["uid"]
                ?.let(::uuid)
                ?.let(::UtbetalingId)
                ?: badRequest("Mangler path parameter 'uid'")

            val dto = call.receive<UtbetalingApi>().also { it.validate() }
            val existing = utbetalingService.lastOrNull(uid) ?: notFound("Fant ikke utbetaling med uid ${uid.id}")
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
                ?: badRequest("Mangler path parameter 'uid'")

            val dto = call.receive<UtbetalingApi>().also { it.validate() }
            val existing = utbetalingService.lastOrNull(uid) ?: notFound("Fant ikke utbetaling med uid ${uid.id}")
            val domain = Utbetaling.from(dto, existing.lastPeriodeId)

            utbetalingService.delete(uid, domain).onFailure {
                when (it) {
                    DatabaseError.Conflict -> conflict("Utbetaling finnes allerede")
                    DatabaseError.Unknown -> internalServerError("Ukjent databasefeil, helved har blitt varslet")
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        route("/simuler") {
            post {
                val uid = call.parameters["uid"]
                    ?.let(::uuid)
                    ?.let(::UtbetalingId)
                    ?: badRequest("Mangler path parameter 'uid'")

                val dto = call.receive<UtbetalingApi>().also { it.validate() }
                val domain = Utbetaling.from(dto)
                val token = call.getTokenType() ?: unauthorized("Mangler claim, enten azp_name eller NAVident")

                val response = simuleringService.simuler(uid, domain, token)

                call.respond(HttpStatusCode.OK, response)
            }
            delete {
                val uid = call.parameters["uid"]
                    ?.let(::uuid)
                    ?.let(::UtbetalingId)
                    ?: badRequest("Mangler path parameter 'uid'")

                val dto = call.receive<UtbetalingApi>().also { it.validate() }
                val token = call.getTokenType() ?: unauthorized("Mangler claim, enten azp_name eller NAVident")
                val existing = utbetalingService.lastOrNull(uid) ?: notFound("Fant ikke utbetaling med uid ${uid.id}")
                val domain = Utbetaling.from(dto, existing.lastPeriodeId)
                val response = simuleringService.simulerDelete(uid, domain, token)
                call.respond(HttpStatusCode.OK, response)
            }
        }

    }
}

fun RoutingCall.getTokenType(): TokenType? {
    return if(hasClaim("NAVident")) {
        TokenType.Obo(request.authorization()?.replace("Bearer ", "") ?: unauthorized("auth header missing"))
    } else if (hasClaim("azp_name")) {
        TokenType.Client(request.authorization()?.replace("Bearer ", "") ?: unauthorized("auth header missing"))
    } else {
        unauthorized("Mangler claim, enten azp_name eller NAVident")
    }
}

private fun uuid(str: String): UUID {
    return try {
        UUID.fromString(str)
    } catch (e: Exception) {
        badRequest("Path param må være UUID")
    }
}

