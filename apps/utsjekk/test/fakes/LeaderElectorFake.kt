package fakes

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import port
import utsjekk.LeaderElectionResponse
import java.net.InetAddress
import java.net.URI
import java.time.LocalDateTime

class LeaderElectorFake : AutoCloseable {
    private val elector = embeddedServer(Netty, port = 4040, module = Application::elector).apply { start() }
    val url by lazy { "http://localhost:${elector.port()}".let(::URI).toURL() }
    override fun close() = elector.stop(0, 0)
}

private fun Application.elector() {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
    routing {
        get {
            call.respond(
                LeaderElectionResponse(
                    name = InetAddress.getLocalHost().hostName,
                    last_update = LocalDateTime.now()
                )
            )
        }
    }
}
