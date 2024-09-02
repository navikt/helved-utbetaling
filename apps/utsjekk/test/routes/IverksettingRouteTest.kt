package routes

import TestData
import TestRuntime
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import httpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.Satstype
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.iverksett.IverksettStatus
import no.nav.utsjekk.kontrakter.oppdrag.OppdragIdDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatusDto
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import repeatUntil
import java.time.LocalDate

class IverksettingRouteTest {
    @AfterEach
    fun reset() {
        TestRuntime.unleash.reset()
    }

    @Test
    fun `iverksetter ikke når kill switch for ytelsen er skrudd på`() = runTest {
        TestRuntime.unleash.disable(Fagsystem.TILLEGGSSTØNADER)

        val dto = TestData.dto.iverksetting()

        val res = httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, res.status)
        assertEquals("Iverksetting er skrudd av for fagsystem ${Fagsystem.TILLEGGSSTØNADER}", res.bodyAsText())
    }

    @Test
    fun `start iverksetting`() = runTest {
        val dto = TestData.dto.iverksetting()

        val res = httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
        }

        assertEquals(HttpStatusCode.Accepted, res.status)
    }

    @Test
    fun `start iverksetting av tilleggsstønader`() = runTest {
        val dto = TestData.dto.iverksetting(
            iverksettingId = "en-iverksetting",
            vedtak = TestData.dto.vedtaksdetaljer(
                utbetalinger = listOf(
                    TestData.dto.utbetaling(
                        satstype = Satstype.MÅNEDLIG,
                        fom = LocalDate.of(2021, 1, 1),
                        tom = LocalDate.of(2021, 1, 31),
                        stønadsdata = TestData.dto.tilleggstønad()
                    )
                )
            )
        )

        httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
        }.let {
            assertEquals(HttpStatusCode.Accepted, it.status)
        }

        suspend fun getStatus(): IverksettStatus =
            httpClient.get("/api/iverksetting/${dto.sakId}/${dto.behandlingId}/${dto.iverksettingId}/status") {
                bearerAuth(TestRuntime.azure.generateToken())
                accept(ContentType.Application.Json)
            }.body()

        val status = runBlocking {
            repeatUntil(::getStatus) { status ->
                status == IverksettStatus.SENDT_TIL_OPPDRAG
            }
        }

        assertEquals(IverksettStatus.SENDT_TIL_OPPDRAG, status)
    }


    @Test
    fun `start iverksetting av vedtak uten utbetaling`() = runTest {
        val dto =
            TestData.dto.iverksetting(
                vedtak = TestData.dto.vedtaksdetaljer(
                    utbetalinger = emptyList(),
                ),
            )

        httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
        }.let {
            assertEquals(HttpStatusCode.Accepted, it.status)
        }

        suspend fun getStatus(): IverksettStatus =
            httpClient.get("/api/iverksetting/${dto.sakId}/${dto.behandlingId}/status") {
                bearerAuth(TestRuntime.azure.generateToken())
                accept(ContentType.Application.Json)
            }.body()

        val status = runBlocking {
            repeatUntil(::getStatus) { status ->
                status == IverksettStatus.OK_UTEN_UTBETALING
            }
        }

        assertEquals(IverksettStatus.OK_UTEN_UTBETALING, status)
        assertTrue(TestRuntime.kafka.produced.containsKey(dto.personident.verdi))
    }

    @Test
    fun `returnerer beskrivende feilmelding når jackson ikke greier å deserialisere request`() = runTest {
        @Language("JSON")
        val payload =
            """
            {
              "behandlingId": "1",
              "forrigeIverksetting": null,
              "personident": {
                "verdi": "15507600333"
              },
              "sakId": "1234",
              "vedtak": {
                "vedtakstidspunkt": "2021-05-12T00:00:00",
                "saksbehandlerId": "A12345",
                "utbetalinger": [
                  {
                    "beløp": 500,
                    "satstype": "DAGLIG",
                    "fraOgMedDato": "2021-01-01",
                    "tilOgMedDato": "2021-12-31",
                    "stønadsdata": {
                      "stønadstype": "DAGPENGER_ARBEIDSSØKER_ORDINÆR",
                      "ferietillegg": null
                    }
                  }
                ]
              }
            }
            """.trimIndent()

        val res = httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(objectMapper.readValue<JsonNode>(payload))
        }

        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals("Klarte ikke lese request body. Sjekk at du ikke mangler noen felter", res.bodyAsText())
    }

    @Disabled
    @Test
    fun `iverksetting blir kvittert ok`() = runTest {
        val dto = TestData.dto.iverksetting()

        val res = httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
        }

        assertEquals(HttpStatusCode.Accepted, res.status)

        suspend fun getStatus(): IverksettStatus =
            httpClient.get("/api/iverksetting/${dto.sakId}/${dto.behandlingId}/status") {
                bearerAuth(TestRuntime.azure.generateToken())
                accept(ContentType.Application.Json)
            }.body()

        val oppdragId = OppdragIdDto(
            fagsystem = Fagsystem.TILLEGGSSTØNADER,
            sakId = dto.sakId,
            behandlingId = dto.behandlingId,
            iverksettingId = dto.iverksettingId,
        )
        TestRuntime.oppdrag.setExpected(
            OppdragStatusDto(status = OppdragStatus.KVITTERT_OK, feilmelding = null),
            oppdragId
        )

        val status = runBlocking {
            repeatUntil(::getStatus) { status ->
                status == IverksettStatus.OK
            }
        }

        assertEquals(IverksettStatus.OK, status)
    }
}
