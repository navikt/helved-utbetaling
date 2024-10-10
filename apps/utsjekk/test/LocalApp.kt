import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withContext
import libs.auth.AzureToken
import libs.postgres.Jdbc
import libs.postgres.concurrency.transaction
import libs.task.TaskDao
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.felles.objectMapper
import utsjekk.appLog
import utsjekk.database
import utsjekk.iverksetting.*
import utsjekk.iverksetting.resultat.IverksettingResultatDao
import utsjekk.server
import utsjekk.task.Status
import java.time.LocalDateTime
import java.util.*

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    embeddedServer(Netty, port = 8080, module = Application::testApp).start(wait = true)
}

fun Application.testApp() {
    database(TestRuntime.config.jdbc)
    server(
        config = TestRuntime.config,
        featureToggles = TestRuntime.unleash,
        statusProducer = TestRuntime.kafka,
    ).apply {
        routing {
            get("/token") {
                call.respond(AzureToken(3600, TestRuntime.azure.generateToken()))
            }

            post("/libs/task") {
                val numberOfTasks = call.request.queryParameters["numberOfTasks"]?.toIntOrNull() ?: 10

                withContext(Jdbc.context) {
                    transaction {
                        for (i in 1..numberOfTasks) {
                            val iverksetting = TestData.domain.iverksetting()

                            iverksetting.dao().insert()
                            iverksetting.resultatDao().insert()

                            enTask(payload = iverksetting).insert()
                        }
                    }
                }

                call.respond(HttpStatusCode.Created)
            }
        }
    }
}

private fun Iverksetting.dao(): IverksettingDao = IverksettingDao(this, mottattTidspunkt = LocalDateTime.now())

private fun Iverksetting.resultatDao(): IverksettingResultatDao =
    IverksettingResultatDao(
        fagsystem = this.fagsak.fagsystem,
        sakId = this.sakId,
        behandlingId = this.behandlingId,
        iverksettingId = this.iverksettingId,
    )

private fun enTask(
    status: Status = Status.IN_PROGRESS,
    createdAt: LocalDateTime = LocalDateTime.now(),
    payload: Iverksetting,
) = TaskDao(
    id = UUID.randomUUID(),
    kind = libs.task.Kind.Iverksetting,
    payload = objectMapper.writeValueAsString(payload),
    status = libs.task.Status.valueOf(status.name),
    attempt = 0,
    createdAt = createdAt,
    updatedAt = createdAt,
    scheduledFor = createdAt,
    message = null,
)
