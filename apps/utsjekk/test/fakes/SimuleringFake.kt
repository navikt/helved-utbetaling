package fakes

import TestData
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import libs.ktor.port
import utsjekk.SimuleringConfig
import utsjekk.simulering.client
import utsjekk.utbetaling.UtbetalingsoppdragDto
import java.net.URI

class SimuleringFake : AutoCloseable {
    private val simulering = embeddedServer(Netty, port = 0, module = Application::simulering).apply { start() }

    val config by lazy {
        SimuleringConfig(
            host = "http://localhost:${simulering.engine.port}".let(::URI).toURL(),
            scope = "test"
        )
    }

    fun respondWith(res: Any, statusCode: HttpStatusCode = HttpStatusCode.OK) {
        simuleringResponse = res
        simuleringResponseCode = statusCode
    }

    fun reset() {
        simuleringResponse = TestData.dto.client.simuleringResponse()
        simuleringResponseCode = HttpStatusCode.OK
    }

    override fun close() = simulering.stop(0, 0)
}

private var simuleringResponse: Any = TestData.dto.client.simuleringResponse()
private var simuleringResponseCode: HttpStatusCode = HttpStatusCode.OK

private fun Application.simulering() {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    routing {
        post("/simuler") {
            // val req = call.receive<UtbetalingsoppdragDto>()
            call.respond(simuleringResponseCode,simuleringResponse)
        }
        post("/simulering") {
            // val req = call.receive<client.SimuleringRequest>()
            call.respond(simuleringResponseCode,simuleringResponse)
        }
    }
}
