package fakes

import TestData
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import port
import utsjekk.SimuleringConfig
import utsjekk.simulering.client
import java.net.URI

class SimuleringFake : AutoCloseable {
    private val simulering = embeddedServer(Netty, port = 0, module = Application::simulering).apply { start() }

    val config by lazy {
        SimuleringConfig(
            host = "http://localhost:${simulering.engine.port}".let(::URI).toURL(),
            scope = "test"
        )
    }

    fun respondWith(res: client.SimuleringResponse) {
        simuleringResponse = res
    }

    fun reset() {
        simuleringResponse = TestData.dto.client.simuleringResponse()
    }

    override fun close() = simulering.stop(0, 0)
}

private var simuleringResponse = TestData.dto.client.simuleringResponse()

private fun Application.simulering() {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    routing {
        post("/simulering") {
            val req = call.receive<client.SimuleringRequest>()
            call.respond(simuleringResponse)
        }
    }
}