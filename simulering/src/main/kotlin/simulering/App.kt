package simulering

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import felles.appLog

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e -> appLog.error("Uhåndtert feil", e) }
    embeddedServer(Netty, port = 8080, module = Application::simulering).start(wait = true)
}

fun Application.simulering() {

}
