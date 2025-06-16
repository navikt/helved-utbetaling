package fakes

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URI
import libs.ktor.port
import utsjekk.AbetalConfig
import utsjekk.utbetaling.Utbetaling

class AbetalClientFake : AutoCloseable {
    companion object {
        val response = mutableListOf<Utbetaling>()
        fun server(app: Application) {
            app.install(ContentNegotiation) {
                jackson {
                    registerModule(JavaTimeModule())
                    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                }
            }
            app.routing {
                get("/api/utbetalinger") {
                    val uid = call.request.queryParameters["uid"]
                    if (response.isNotEmpty()) {
                        val utbetaling = response.find { it.toString() == uid }
                        if (utbetaling != null) {
                            call.respond(utbetaling)
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }
            }
        }
    }

    private val server = embeddedServer(Netty, port = 0) { server(this) }.apply { start() }
    val config by lazy {
        AbetalConfig(
            host = "http://localhost:${server.engine.port}".let(::URI).toURL(),
            scope = "test"
        )
    }

    override fun close() = server.stop(0, 0)
}
