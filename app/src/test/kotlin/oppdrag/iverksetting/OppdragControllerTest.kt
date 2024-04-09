package oppdrag.iverksetting

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import kotlinx.coroutines.withTimeout
import no.nav.dagpenger.kontrakter.oppdrag.OppdragStatus
import no.nav.dagpenger.kontrakter.oppdrag.Utbetalingsoppdrag
import oppdrag.TestEnvironment
import oppdrag.etUtbetalingsoppdrag
import oppdrag.iverksetting.tilstand.OppdragId
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import oppdrag.oppdrag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OppdragControllerTest {

    @Test
    fun `skal lagre oppdrag for utbetalingoppdrag`() {
        val utbetalingsoppdrag = etUtbetalingsoppdrag()
        var oppdragStatus: OppdragStatus = OppdragStatus.LAGT_PÅ_KØ

        testApplication {
            application { oppdrag(TestEnvironment.config) }

            httpClient.post("/oppdrag") {
                contentType(ContentType.Application.Json)
                setBody(utbetalingsoppdrag)
            }

            withTimeout(1_000) {
                TestEnvironment.transaction { con ->
                    while (oppdragStatus == OppdragStatus.LAGT_PÅ_KØ) {
                        oppdragStatus = OppdragLagerRepository.hentOppdrag(utbetalingsoppdrag.oppdragId, con).status
                    }
                }
            }
            assertEquals(OppdragStatus.KVITTERT_OK, oppdragStatus)
        }
    }

    @Test
    fun `skal returnere https statuscode 409 ved dobbel sending`() {
        val utbetalingsoppdrag = etUtbetalingsoppdrag()
        var oppdragStatus: OppdragStatus = OppdragStatus.LAGT_PÅ_KØ

        testApplication {
            application { oppdrag(TestEnvironment.config) }

            httpClient.post("/oppdrag") {
                contentType(ContentType.Application.Json)
                setBody(utbetalingsoppdrag)
            }.also {
                assertEquals(HttpStatusCode.Created, it.status)
            }

            httpClient.post("/oppdrag") {
                contentType(ContentType.Application.Json)
                setBody(utbetalingsoppdrag)
            }.also {
                assertEquals(HttpStatusCode.Conflict, it.status)
            }

            withTimeout(1_000) {
                TestEnvironment.transaction { con ->
                    while (oppdragStatus == OppdragStatus.LAGT_PÅ_KØ) {
                        oppdragStatus = OppdragLagerRepository.hentOppdrag(utbetalingsoppdrag.oppdragId, con).status
                    }
                }
            }

            assertEquals(OppdragStatus.KVITTERT_OK, oppdragStatus)
        }
    }

    private val ApplicationTestBuilder.httpClient: HttpClient
        get() =
            createClient {
                install(ContentNegotiation) {
                    jackson {
                        registerModule(JavaTimeModule())
                        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    }
                }
            }
}

private val Utbetalingsoppdrag.oppdragId
    get() =
        OppdragId(
            fagsystem = this.fagsystem,
            fagsakId = this.saksnummer,
            behandlingId = this.utbetalingsperiode[0].behandlingId,
            iverksettingId = this.iverksettingId,
        )
