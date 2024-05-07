package utsjekk.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import libs.task.AvvikshåndterDTO
import libs.task.KommentarDTO
import libs.task.Status
import libs.task.TaskService

private fun ApplicationCall.navident(): String? {
    return principal<JWTPrincipal>()
        ?.getClaim("NAVident", String::class)
}

fun Route.task(service: TaskService) {

    route("/api/task") {

        get("/{id}") {
            val id = call.parameters["id"]?.toLong()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "mangler param id")

            val navident = call.navident()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val task = service.hentTaskMedId(id, navident)
                ?: return@get call.respond(HttpStatusCode.NotFound)

            call.respond(task)
        }

        get("/v2") {
            val status = call.request.queryParameters["status"]
                ?.let { listOf(Status.valueOf(it)) }
                ?: emptyList()

            val type = call.request.queryParameters["type"]

            val navident = call.navident()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val tasks = service.hentTasks(status, navident, type)
            call.respond(tasks)
        }

        get("/ferdigNaaFeiletFoer") {
            val navident = call.navident()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val tasks = service.hentTasksSomErFerdigNåMenFeiletFør(navident)
            call.respond(tasks)
        }

        get("/antall-til-oppfolging") {
            val antall = service.finnAntallTaskerSomKreverOppfølging()
            call.respond(antall)
        }

        get("antall-feilet-og-manuell-oppfolging") {
            val antall = service.finnAntallTaskerMedStatusFeiletOgManuellOppfølging()
            call.respond(antall)
        }

        get("/logg/{id}") {
            val id = call.parameters["id"]?.toLong()
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val navident = call.navident()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val taskLogs = service.getTaskLogs(id, navident)
            call.respond(taskLogs)
        }

        put("/rekjor") {
            val taskId = call.request.queryParameters["taskId"]?.toLong()
                ?: return@put call.respond(HttpStatusCode.BadRequest, "mangler query param taskId")

            val navident = call.navident()
                ?: return@put call.respond(HttpStatusCode.Unauthorized)

            val result = service.rekjørTask(taskId, navident)
            call.respond(result)
        }

        put("/rekjorAlle") {
            val status = call.request.header("status")?.let { Status.valueOf(it) }
                ?: return@put call.respond(HttpStatusCode.BadRequest, "mangler header status")

            val navident = call.navident()
                ?: return@put call.respond(HttpStatusCode.Unauthorized)

            val result = service.rekjørTasks(status, navident)
            call.respond(result)
        }

        put("/avvikshaandter") {
            val navident = call.navident()
                ?: return@put call.respond(HttpStatusCode.Unauthorized)

            val taskId = call.request.queryParameters["taskId"]?.toLong()
                ?: return@put call.respond(HttpStatusCode.BadRequest, "mangler query param taskId")

            val avvikshåndter = call.receive<AvvikshåndterDTO>()

            val result = service.avvikshåndterTask(taskId, avvikshåndter.avvikstype, avvikshåndter.årsak, navident)
            call.respond(result)
        }

        put("/kommenter") {
            val navident = call.navident()
                ?: return@put call.respond(HttpStatusCode.Unauthorized)

            val taskId = call.request.queryParameters["taskId"]?.toLong()
                ?: return@put call.respond(HttpStatusCode.BadRequest, "mangler query param taskId")

            val kommentar = call.receive<KommentarDTO>()

            val result = service.kommenterTask(taskId, kommentar, navident)
            call.respond(result)
        }
    }
}