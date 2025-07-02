import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.time.LocalDateTime
import java.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.auth.AzureToken
import libs.jdbc.Jdbc
import libs.jdbc.Migrator
import libs.jdbc.concurrency.transaction
import libs.utils.*
import models.kontrakter.felles.objectMapper
import utsjekk.*
import utsjekk.iverksetting.*
import utsjekk.iverksetting.resultat.IverksettingResultatDao
import utsjekk.utbetaling.UtbetalingId

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    embeddedServer(Netty, port = 8080, module = Application::testApp).start(wait = true)
}

fun Application.testApp() {
    val config = TestRuntime.config

    runBlocking {
        Jdbc.initialize(config.jdbc)
        withContext(Jdbc.context) {
            Migrator(
                listOf(
                    File("migrations"),
                    File("test/utsjekk/utbetaling/migrations")
                )
            ).migrate()
        }
        appLog.info("setup database")
    }
    utsjekk(TestRuntime.config, TestRuntime.kafka)
    testRouting()
}

fun Application.testRouting() {
    routing {
        get("/token") {
            call.respond(AzureToken(3600, TestRuntime.azure.generateToken()))
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

