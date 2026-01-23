package utsjekk.routing

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import libs.utils.onFailure
import models.*
import utsjekk.TokenType
import utsjekk.hasClaim
import utsjekk.utbetaling.*
import utsjekk.utbetaling.Utbetaling
import utsjekk.utbetaling.UtbetalingId
import utsjekk.utbetaling.simulering.SimuleringService
import java.util.*

// TODO: valider at stønad (enum) tilhører AZP (claims)
fun Route.utbetalinger(
    simuleringService: SimuleringService,
    utbetalingService: UtbetalingService,
    utbetalingMigrator: UtbetalingMigrator,
) {

    route("/utbetalinger/{uid}") {

        post("/migrate") {
            val uid = call.parameters["uid"]
                ?.let(::uuid)
                ?.let(::UtbetalingId)
                ?: badRequest("Mangler path parameter 'uid'")

            val request = call.receive<MigrationRequest>()
            utbetalingMigrator.transfer(uid, request)
            call.respond(HttpStatusCode.OK)
        }

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

