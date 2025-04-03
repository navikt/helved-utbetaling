package vedskiva

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import libs.utils.logger
import libs.utils.secureLog
import models.erHelligdag
import java.time.LocalDate

val appLog = logger("app")

fun main(args: Array<String>) {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }
    embeddedServer(Netty, port = 8080) { vedskiva(lastAvstemmingsdag = LocalDate.now().minusDays(1)) }.start(wait = true)
}

fun Application.vedskiva(
    config: Config = Config(),
    kafka: Kafka = Kafka(),
    lastAvstemmingsdag: LocalDate
) {
    if (!LocalDate.now().erHelligdag()) {
        OppdragsdataConsumer(config.kafka, kafka).use {
            it.consumeFromBeginning(lastAvstemmingsdag)
        }
    }

    routing {
        route("/probes") {
            get("/health") {
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
