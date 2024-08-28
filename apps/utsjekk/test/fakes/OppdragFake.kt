package fakes

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import port
import utsjekk.OppdragConfig
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
    install(ContentNegotiation) {
        jackson { }
    }

    routing {
        post("/oppdrag") {
            call.respond(HttpStatusCode.OK)
        }
    }
}
