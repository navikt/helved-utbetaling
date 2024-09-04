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
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import no.nav.utsjekk.kontrakter.oppdrag.*
import port
import utsjekk.OppdragConfig
import java.net.URI

class OppdragFake : AutoCloseable {
    private val oppdrag = embeddedServer(Netty, port = 0) { azure(this@OppdragFake) }.apply { start() }

    val config by lazy {
        OppdragConfig(
            host = "http://localhost:${oppdrag.port()}".let(::URI).toURL(),
            scope = "test"
        )
    }

    fun respondWith(status: OppdragStatusDto, id: OppdragIdDto) {
        oppdragMap[id] = status
    }

    fun reset() {
        oppdragMap.clear()
        expectedStatusRequestBody = CompletableDeferred()
        expectedAvstemming = CompletableDeferred()
    }

    var expectedStatusRequestBody = CompletableDeferred<OppdragIdDto>()
    var expectedAvstemming = CompletableDeferred<GrensesnittavstemmingRequest>()

    override fun close() = oppdrag.stop(0, 0)
}

private val oppdragMap = mutableMapOf<OppdragIdDto, OppdragStatusDto>()

private fun Application.azure(oppdragFake: OppdragFake) {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    routing {
        post("/oppdrag") {
            val dto = call.receive<Utbetalingsoppdrag>()
            val oppdragIdDto = OppdragIdDto(
                dto.fagsystem,
                dto.saksnummer,
                dto.utbetalingsperiode.first().behandlingId,
                dto.iverksettingId,
            )
            if (!oppdragMap.containsKey(oppdragIdDto)) {
                oppdragMap[oppdragIdDto] = OppdragStatusDto(OppdragStatus.LAGT_PÅ_KØ, feilmelding = null)
            }
            call.respond(HttpStatusCode.OK)
        }

        post("/status") {
            val dto = call.receive<OppdragIdDto>()

            if (oppdragMap.containsKey(dto)) {
                oppdragFake.expectedStatusRequestBody.complete(dto)
            }

            when (val status = oppdragMap[dto]) {
                null -> call.respond(HttpStatusCode.NotFound, "Fant'n ikke")
                else -> call.respond(HttpStatusCode.OK, status)
            }
        }

        post("/grensesnittavstemming") {
            val dto = call.receive<GrensesnittavstemmingRequest>()
            oppdragFake.expectedAvstemming.complete(dto)
            call.respond(HttpStatusCode.Created)
        }
    }
}
