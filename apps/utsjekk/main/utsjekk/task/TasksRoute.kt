package utsjekk.task

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withContext
import libs.postgres.Jdbc
import libs.postgres.concurrency.transaction
import libs.task.Order
import libs.task.TaskDao
import libs.task.TaskHistory
import libs.task.Tasks
import java.time.LocalDateTime
import java.util.UUID

fun Route.tasks() {
    route("/api/tasks") {
        get {
            val status = call.parameters["status"]?.split(",")?.map { libs.task.Status.valueOf(it) }
            val after = call.parameters["after"]?.let { LocalDateTime.parse(it) }
            val kind = call.parameters["kind"]?.split(",")?.map { libs.task.Kind.valueOf(it) }
            val payload = call.parameters["payload"]

            val page = call.parameters["page"]?.toInt()
            val pageSize = call.parameters["pageSize"]?.toInt() ?: 20

            val order = Order("scheduled_for", Order.Direction.DESCENDING)

            withContext(Jdbc.context) {
                if (page != null) {
                    val tasks =
                        Tasks.filterBy(
                            status = status,
                            after = after,
                            kind = kind,
                            payload = payload,
                            limit = pageSize,
                            offset = (page - 1) * pageSize,
                            order = order,
                        ).map(TaskDto::from)
                    val count = Tasks.count(
                        status = status,
                        after = after,
                        kind = kind,
                        payload = payload
                    )
                    call.respond(PaginatedTasksDto(tasks, page, pageSize, count))
                } else {
                    val tasks = Tasks.filterBy(
                        status = status,
                        after = after,
                        kind = kind,
                        payload = payload,
                        order = order
                    )
                    call.respond(tasks)
                }
            }
        }

        put("/rerun") {
            val status = call.parameters["status"]?.split(",")?.map { libs.task.Status.valueOf(it) }
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Mangler påkrevd query parameter 'status'")
            val kind = call.parameters["kind"]?.split(",")?.map { libs.task.Kind.valueOf(it) }
                ?: emptyList()

            withContext(Jdbc.context) {
                Tasks.rerunAll(status, kind)
                call.respond(HttpStatusCode.OK)
            }
        }

        put("/{id}/rerun") {
            val id = call.parameters["id"]?.let(UUID::fromString)
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Mangler påkrevd path parameter 'id'")

            withContext(Jdbc.context) {
                transaction {
                    TaskDao.select { it.id = id }
                }
            }.singleOrNull()
                ?: return@put call.respond(HttpStatusCode.NotFound, "Fant ikke task med id $id")

            withContext(Jdbc.context) {
                Tasks.rerun(id)
                call.respond(HttpStatusCode.OK)
            }
        }

        put("/{id}/stop") {
            val id = call.parameters["id"]?.let(UUID::fromString)
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Mangler påkrevd path parameter 'id'")

            withContext(Jdbc.context) {
                transaction {
                    TaskDao.select { it.id = id }
                }
            }.singleOrNull()
                ?: return@put call.respond(HttpStatusCode.NotFound, "Fant ikke task med id $id")

            withContext(Jdbc.context) {
                Tasks.stop(id)
                call.respond(HttpStatusCode.OK)
            }
        }

        patch("/{id}") {
            val id = call.parameters["id"]?.let(UUID::fromString)
                ?: return@patch call.respond(HttpStatusCode.BadRequest, "Mangler påkrevd path parameter 'id'")

            withContext(Jdbc.context) {
                transaction {
                    TaskDao.select { it.id = id }
                }
            }.singleOrNull()
                ?: return@patch call.respond(HttpStatusCode.NotFound, "Fant ikke task med id $id")

            val payload = call.receive<TaskDtoPatch>()

            withContext(Jdbc.context) {
                Tasks.update(id, libs.task.Status.valueOf(payload.status.name), payload.message) {
                    LocalDateTime.now()
                }
                call.respond(HttpStatusCode.OK)
            }
        }

        get("/{id}/history") {
            val id = call.parameters["id"]?.let(UUID::fromString)
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Mangler påkrevd path parameter 'id'")

            withContext(Jdbc.context) {
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
