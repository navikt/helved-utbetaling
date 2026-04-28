import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.auth.AzureToken
import libs.jdbc.Jdbc
import libs.jdbc.Migrator
import libs.jdbc.context
import libs.utils.*
import utsjekk.*

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
        val ds = Jdbc.initialize(config.jdbc)
        val jdbcCtx = ds.context()
        withContext(jdbcCtx) {
            Migrator(
                listOf(
                    File("migrations"),
                    File("test/utsjekk/utbetaling/migrations")
                )
            ).migrate()
        }
        appLog.info("setup database")
    }
    utsjekk(TestRuntime.config, TestRuntime.kafka, startupValidation = {})
    testRouting()
}

fun Application.testRouting() {
    routing {
        get("/token") {
            call.respond(AzureToken(3600, TestRuntime.azure.generateToken()))
        }
    }
}
