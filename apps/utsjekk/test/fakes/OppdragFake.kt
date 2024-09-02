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
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import no.nav.utsjekk.kontrakter.oppdrag.OppdragIdDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatusDto
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
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

    fun setExpected(status: OppdragStatusDto, id: OppdragIdDto) {
        oppdragMap[id] = status
    }

    val expectedStatus = CompletableDeferred<OppdragIdDto>()

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
            oppdragMap[OppdragIdDto(
                dto.fagsystem,
                dto.saksnummer,
                dto.utbetalingsperiode.first().behandlingId,
                dto.iverksettingId,
            )] = OppdragStatusDto(OppdragStatus.LAGT_PÅ_KØ, feilmelding = null)
            call.respond(HttpStatusCode.OK)
        }

        post("/status") {
            val dto = call.receive<OppdragIdDto>()
            oppdragFake.expectedStatus.complete(dto)

            val oppdragStatus = oppdragMap[dto]

            if (oppdragStatus != null) {
                call.respond(HttpStatusCode.OK, oppdragStatus)
            } else {
                call.respond(HttpStatusCode.NotFound, "Fant'n ikke")
            }
        }
    }
}
