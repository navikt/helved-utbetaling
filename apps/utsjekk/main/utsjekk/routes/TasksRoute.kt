package utsjekk.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withContext
import libs.postgres.concurrency.transaction
import utsjekk.task.*
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.CoroutineContext

private fun ApplicationCall.navident(): String? {
    return principal<JWTPrincipal>()
        ?.getClaim("NAVident", String::class)
}

fun Route.tasks(context: CoroutineContext) {
    route("/api/tasks") {
        get {
            val status = call.parameters["status"]?.split(",")?.map { Status.valueOf(it) }
            val after = call.parameters["after"]?.let { LocalDateTime.parse(it) }
            val kind = call.parameters["kind"]?.let { Kind.valueOf(it) }

            withContext(context) {
                val tasks = Tasks.filterBy(status, after, kind)

                call.respond(tasks)
            }

        }

        patch("/{id}") {
            val id = call.parameters["id"]?.let(UUID::fromString)
                ?: return@patch call.respond(HttpStatusCode.BadRequest, "mangler påkrevd path parameter 'id'")

            withContext(context) {
                transaction {
                    TaskDao.select { it.id = id }
                }
            }.singleOrNull() ?: return@patch call.respond(HttpStatusCode.NotFound, "Fant ikke task med id $id")

            val payload = call.receive<TaskDtoPatch>()
            Tasks.update(id, payload.status, payload.message)
        }

        get("/{id}/history") {
            val id = call.parameters["id"]?.let(UUID::fromString)
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Mangler påkrevd path parameter 'id'")

            withContext(context) {
                val historikk = transaction { TaskHistory.history(id) }

                call.respond(historikk)
            }
        }
    }
}

data class TaskDtoPatch(
    val status: Status,
    val message: String,
)