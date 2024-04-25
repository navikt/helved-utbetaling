package oppdrag.iverksetting

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.withTimeout
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import oppdrag.*
import oppdrag.iverksetting.tilstand.OppdragLager
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OppdragTest {

    @AfterEach
    fun cleanup() = TestRuntime.cleanup()

    @Test
    fun `POST oppdrag svarer 201`() {
        val utbetalingsoppdrag = etUtbetalingsoppdrag()

        testApplication {
            application { server(TestRuntime.config) }

            httpClient.post("/oppdrag") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken())
                setBody(utbetalingsoppdrag)
            }.also {
                assertEquals(HttpStatusCode.Created, it.status)
            }
        }
    }

    @Test
    fun `POST oppdrag svarer 409 ved uplikat`() {
        val utbetalingsoppdrag = etUtbetalingsoppdrag()

        testApplication {
            application { server(TestRuntime.config) }

            httpClient.post("/oppdrag") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken())
                setBody(utbetalingsoppdrag)
            }.also {
                assertEquals(HttpStatusCode.Created, it.status)
            }

            httpClient.post("/oppdrag") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken())
                setBody(utbetalingsoppdrag)
            }.also {
                assertEquals(HttpStatusCode.Conflict, it.status)
            }
        }
    }

    @Test
    fun `utbetaling kvitterer ok`() {
        val periode = enUtbetalingsperiode(behandlingId = "7pptdD10zemjwgB8YWez")
        val utbetaling = etUtbetalingsoppdrag(fagsak = "6rouSucMUIX10SikRKcq", utbetalingsperiode = arrayOf(periode))

        testApplication {
            application {
                server(TestRuntime.config)
            }

            httpClient.post("/oppdrag") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken())
                setBody(utbetaling)
            }.also {
                assertEquals(HttpStatusCode.Created, it.status)
            }

            val xml = Resource.read("/kvittering-ok.xml")

            TestRuntime.oppdrag.kvitteringsKø.produce(xml)

            val oppdrag = repeatUntil(::statusChanged) {
                TestRuntime.postgres.transaction {
                    OppdragLagerRepository.hentOppdrag(utbetaling.oppdragId, it)
                }
            }

            assertEquals(OppdragStatus.KVITTERT_OK, oppdrag.status)
        }
    }

    @Test
    fun `utbetaling kvitterer med mangler`() {

    }

    @Test
    fun `utbetaling kvitterer funksjonell feil`() {

    }

    @Test
    fun `utbetaling kvitterer teknisk feil`() {

    }

    @Test
    fun `utbetaling kvitterer ukjent`() {

    }
}

private fun statusChanged(oppdrag: OppdragLager): Boolean {
    return oppdrag.status != OppdragStatus.LAGT_PÅ_KØ
}

private suspend fun <T> repeatUntil(
    predicate: (T) -> Boolean,
    timeoutMs: Long = 1_000,
    action: () -> T,
): T = withTimeout(timeoutMs) {
    var result = action()
    while (!predicate(result)) result = action()
    result
}
