package utsjekk.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import kotlinx.coroutines.withContext
import libs.postgres.concurrency.transaction
import utsjekk.task.Status
import utsjekk.task.TaskDao
import utsjekk.task.TaskHistoryDao
import utsjekk.task.Tasks
import java.time.LocalDateTime
import java.util.UUID
import kotlin.coroutines.CoroutineContext

private fun ApplicationCall.navident(): String? {
    return principal<JWTPrincipal>()
        ?.getClaim("NAVident", String::class)
}

fun Route.tasks(context: CoroutineContext) {
    route("/api/tasks") {
        get {
            val status = call.parameters["status"]
            val after = call.parameters["after"]

            withContext(context) {
                val tasks = when {
                    status != null -> Tasks.forStatus(Status.valueOf(status))
                    after != null -> Tasks.createdAfter(LocalDateTime.parse(after))
                    else ->
                        transaction {
                            TaskDao.select(status = Status.entries)
                                .map(TaskDto::from)
                        }
                }

                call.respond(tasks)
            }

        }

        patch("/{id}") {
            val id = call.parameters["id"]?.let(UUID::fromString)
                ?: return@patch call.respond(HttpStatusCode.BadRequest, "mangler påkrevd path parameter 'id'")

            transaction { TaskDao.select(id = id) }.singleOrNull()
                ?: return@patch call.respond(HttpStatusCode.NotFound, "Fant ikke task med id $id")

            val payload = call.receive<TaskDtoPatch>()
            Tasks.update(id, payload.status, payload.message)
        }

        get("/{id}/history") {
            val id = call.parameters["id"]?.let(UUID::fromString)
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Mangler påkrevd path parameter 'id'")

            withContext(context) {
                val historikk = transaction { TaskHistoryDao.select(taskId = id) }

                call.respond(historikk)
            }
        }
    }
}

data class TaskDtoPatch(
    val status: Status,
    val message: String,
)