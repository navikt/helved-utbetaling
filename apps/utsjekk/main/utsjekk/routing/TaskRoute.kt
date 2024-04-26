package utsjekk.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import utsjekk.task.TaskService

fun Routing.task(service: TaskService) {

    route("/api/task") {

        get("/{id}") {
            val id = call.parameters["id"]?.toLong()
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val pid = call.principal<JWTPrincipal>()?.getClaim("pid", String::class)
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val task = service.hentTaskMedId(id, pid)
                ?: return@get call.respond(HttpStatusCode.NotFound)

            call.respond(task)
        }

        get("/v2") {
            TODO("not implemented")
        }

        get("/callId/{callId}") {
            TODO("not implemented")
        }

        get("/ferdigNaaFeiletFoer") {
            TODO("not implemented")
        }

        get("/antall-til-oppfolging") {
            TODO("not implemented")
        }

        get("antall-feilet-og-manuell-oppfolging") {
            TODO("not implemented")
        }

        get("/logg/{id}") {
            TODO("not implemented")
        }

        put("/rekjor") {
            TODO("not implemented")
        }

        put("/rekjorAlle") {
            TODO("not implemented")
        }

        put("/avvikshaandter") {
            TODO("not implemented")
        }

        put("/kommenter") {
            TODO("not implemented")
        }
    }
}
