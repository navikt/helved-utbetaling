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
import models.kontrakter.felles.Fagsystem
import models.kontrakter.oppdrag.*
import libs.ktor.port
import utsjekk.OppdragConfig
import java.net.URI

class OppdragFake : AutoCloseable {
    private val oppdrag = embeddedServer(Netty, port = 0, module = Application::oppdrag).apply { start() }

    val config by lazy {
        OppdragConfig(
            host = "http://localhost:${oppdrag.engine.port}".let(::URI).toURL(),
            scope = "test"
        )
    }

    fun statusRespondWith(id: OppdragIdDto, response: OppdragStatusDto) {
        statuser[id] = FakeResponse(response)
    }

    fun iverksettRespondWith(id: OppdragIdDto, response: HttpStatusCode) {
        iverksettinger[id] = FakeResponse(response)
    }

    fun avstemmingRespondWith(fagsystem: Fagsystem, response: HttpStatusCode) {
        avstemminger[fagsystem] = FakeResponse(response)
    }

    suspend fun awaitIverksett(id: OppdragIdDto) = iverksettinger[id]!!.request.await()
    suspend fun awaitStatus(id: OppdragIdDto) = statuser[id]!!.request.await()
    suspend fun awaitAvstemming(fagsystem: Fagsystem) = avstemminger[fagsystem]!!.request.await()

    fun reset() {
        iverksettinger.clear()
        statuser.clear()
        avstemminger.clear()
    }

    override fun close() = oppdrag.stop(0, 0)
}

data class FakeResponse<T, U>(val response: T) {
    val request: CompletableDeferred<U> = CompletableDeferred()
}

private val iverksettinger = mutableMapOf<OppdragIdDto, FakeResponse<HttpStatusCode, Utbetalingsoppdrag>>()
private val statuser = mutableMapOf<OppdragIdDto, FakeResponse<OppdragStatusDto, OppdragIdDto>>()
private val avstemminger = mutableMapOf<Fagsystem, FakeResponse<HttpStatusCode, GrensesnittavstemmingRequest>>()

private fun Application.oppdrag() {
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

            val fakeResponse = iverksettinger[oppdragIdDto]?.also { it.request.complete(dto) }

            when (fakeResponse) {
                null -> call.respond(HttpStatusCode.Created, "fallback fake response")
                else -> call.respond(fakeResponse.response)
            }
        }

        post("/status") {
            val dto = call.receive<OppdragIdDto>()
            val fakeResponse = statuser[dto]?.also { it.request.complete(dto) }

            when (fakeResponse) {
                null -> call.respond(OppdragStatusDto(OppdragStatus.KVITTERT_UKJENT, "fallback fake response"))
                else -> call.respond(fakeResponse.response)
            }
        }

        post("/grensesnittavstemming") {
            val dto = call.receive<GrensesnittavstemmingRequest>()
            val fakeResponse = avstemminger[dto.fagsystem]?.also { it.request.complete(dto) }

            when (fakeResponse) {
                null -> call.respond(HttpStatusCode.Created, "fallback fake response")
                else -> call.respond(fakeResponse.response)
            }
        }
    }
}
