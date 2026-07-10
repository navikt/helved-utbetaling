package fakes

import TestData
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
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

    /** Set the response for dryrun/v3 proxy requests */
    fun respondDryrunWith(body: String, statusCode: HttpStatusCode = HttpStatusCode.OK) {
        dryrunResponseBody = body
        dryrunResponseCode = statusCode
    }

    fun reset() {
        simuleringResponse = TestData.dto.client.simuleringResponse()
        simuleringResponseCode = HttpStatusCode.OK
        dryrunResponseBody = null
        dryrunResponseCode = HttpStatusCode.OK
    }

    override fun close() = simulering.stop(0, 0)
}

private var simuleringResponse: Any = TestData.dto.client.simuleringResponse()
private var simuleringResponseCode: HttpStatusCode = HttpStatusCode.OK
private var dryrunResponseBody: String? = null
private var dryrunResponseCode: HttpStatusCode = HttpStatusCode.OK

private fun Application.simulering() {
    install(ContentNegotiation) {
        json(libs.kotlinx.KotlinxJson)
    }

    routing {
        post("/simuler/legacy") {
            call.respond(simuleringResponseCode, simuleringResponse)
        }
        post("/api/simulering/v3") {
            val body = dryrunResponseBody ?: """{"error":"no dryrun response configured"}"""
            call.respondText(body, io.ktor.http.ContentType.Application.Json, dryrunResponseCode)
        }
        post("/api/dryrun/{path...}") {
            val body = dryrunResponseBody ?: """{"error":"no dryrun response configured"}"""
            call.respondText(body, io.ktor.http.ContentType.Application.Json, dryrunResponseCode)
        }
    }
}
