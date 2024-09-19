import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.withContext
import libs.auth.AzureToken
import libs.postgres.concurrency.transaction
import libs.utils.appLog
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.felles.objectMapper
import utsjekk.iverksetting.Iverksetting
import utsjekk.iverksetting.IverksettingDao
import utsjekk.iverksetting.behandlingId
import utsjekk.iverksetting.iverksettingId
import utsjekk.iverksetting.resultat.IverksettingResultatDao
import utsjekk.iverksetting.sakId
import utsjekk.task.Kind
import utsjekk.task.Status
import utsjekk.task.TaskDao
import utsjekk.utsjekk
import java.time.LocalDateTime
import java.util.UUID

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    embeddedServer(Netty, port = 8080, module = Application::testApp).start(wait = true)
}

fun Application.testApp() {
    utsjekk(
        config = TestRuntime.config,
        context = TestRuntime.context,
        featureToggles = TestRuntime.unleash,
        statusProducer = TestRuntime.kafka,
    ).apply {
        routing {
            get("/token") {
                call.respond(AzureToken(3600, TestRuntime.azure.generateToken()))
            }

            post("/task") {
                val numberOfTasks = call.request.queryParameters["numberOfTasks"]?.toIntOrNull() ?: 10

                withContext(TestRuntime.context) {
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
    kind = Kind.Iverksetting,
    payload = objectMapper.writeValueAsString(payload),
    status = status,
    attempt = 0,
    createdAt = createdAt,
    updatedAt = createdAt,
    scheduledFor = createdAt,
    message = null,
)
