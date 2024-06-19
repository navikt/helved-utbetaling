package fakes

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import utsjekk.OppdragConfig
import utsjekk.port
import java.net.URI

class OppdragFake : AutoCloseable {
    private val oppdrag = embeddedServer(Netty, port = 0, module = Application::azure).apply { start() }

    val config by lazy {
        OppdragConfig(
            host = "http://localhost:${oppdrag.port()}".let(::URI).toURL(),
            scope = "test"
        )
    }

    override fun close() = oppdrag.stop(0, 0)
}

private fun Application.azure() {
    routing {
        post("/oppdrag") {
            call.respond("nice")
        }
    }
}
