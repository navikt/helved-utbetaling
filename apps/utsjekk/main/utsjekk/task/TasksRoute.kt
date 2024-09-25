package utsjekk.task

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withContext
import libs.postgres.concurrency.transaction
import utsjekk.task.history.TaskHistory
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.CoroutineContext

fun Route.tasks(context: CoroutineContext) {
    route("/api/tasks") {
        get {
            val status = call.parameters["status"]?.split(",")?.map { Status.valueOf(it) }
            val after = call.parameters["after"]?.let { LocalDateTime.parse(it) }
            val kind = call.parameters["kind"]?.let { Kind.valueOf(it) }

            val page = call.parameters["page"]?.toInt()
            val pageSize = call.parameters["pageSize"]?.toInt() ?: 20

            withContext(context) {
                if (page != null) {
                    val tasks = Tasks.filterBy(status, after, kind, pageSize, (page - 1) * pageSize)
                    val count = Tasks.count(status, after, kind)
                    call.respond(PaginatedTasksDto(tasks, page, pageSize, count))
                } else {
                    val tasks = Tasks.filterBy(status, after, kind)
                    call.respond(tasks)
                }
            }
        }

        put("/{id}/rekjør") {
            val id = call.parameters["id"]?.let(UUID::fromString)
                ?: return@put call.respond(HttpStatusCode.BadRequest, "mangler påkrevd path parameter 'id'")

            withContext(context) {
                Tasks.rekjør(id)
            }

            call.respond(HttpStatusCode.OK)
        }

        patch("/{id}") {
            val id =
                call.parameters["id"]?.let(UUID::fromString)
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, "mangler påkrevd path parameter 'id'")

            withContext(context) {
                transaction {
                    TaskDao.select { it.id = id }
                }
            }.singleOrNull() ?: return@patch call.respond(HttpStatusCode.NotFound, "Fant ikke task med id $id")

            val payload = call.receive<TaskDtoPatch>()

            withContext(context) {
                Tasks.update(id, payload.status, payload.message)
            }
        }

        get("/{id}/history") {
            val id =
                call.parameters["id"]?.let(UUID::fromString)
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Mangler påkrevd path parameter 'id'")

            withContext(context) {
                val historikk = transaction { TaskHistory.history(id) }

                call.respond(historikk)
            }
        }
    }
}

data class PaginatedTasksDto(
    val tasks: List<TaskDto>,
    val page: Int,
    val pageSize: Int,
    val totalTasks: Int,
)

data class TaskDtoPatch(
    val status: Status,
    val message: String,
)
