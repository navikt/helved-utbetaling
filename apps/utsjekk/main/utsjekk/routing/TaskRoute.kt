package utsjekk.routing

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import libs.task.Status
import libs.task.Tasks
import java.time.LocalDateTime

private fun ApplicationCall.navident(): String? {
    return principal<JWTPrincipal>()
        ?.getClaim("NAVident", String::class)
}

fun Route.tasks() {
    route("/api/tasks") {
        get {
            val status = call.parameters["status"]
            val after = call.parameters["after"]

            val tasks = when {
                status != null -> Tasks.forStatus(Status.valueOf(status))
                after != null -> Tasks.createdAfter(LocalDateTime.parse(after))
                else -> Tasks.incomplete()
            }

            call.respond(tasks)
        }
    }
}