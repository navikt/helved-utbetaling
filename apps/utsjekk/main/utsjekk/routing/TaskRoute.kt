package utsjekk.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import libs.postgres.concurrency.transaction
import libs.task.Status
import libs.task.TaskDao
import libs.task.Tasks
import java.time.LocalDateTime
import java.util.*

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

        patch("/{id}") {
            val id = call.parameters["id"]?.let(UUID::fromString)
                ?: return@patch call.respond(HttpStatusCode.BadRequest, "mangler påkrevd path parameter 'id'")

            transaction { TaskDao.select(id = id) }.singleOrNull()
                ?: return@patch call.respond(HttpStatusCode.NotFound, "Fant ikke task med id $id")

            val payload = call.receive<TaskDtoPatch>()
            Tasks.update(id, payload.status, payload.message)
        }
    }
}

data class TaskDtoPatch(
    val status: Status,
    val message: String,
)