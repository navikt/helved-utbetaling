package routes

import TestData
import TestRuntime
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import httpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.Satstype
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.iverksett.IverksettStatus
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class IverksettingRouteTest {
    @AfterEach
    fun reset() {
        TestRuntime.unleash.reset()
    }

    @Test
    fun `iverksetter ikke når kill switch for ytelsen er skrudd på`() = runTest {
        TestRuntime.unleash.disable(Fagsystem.DAGPENGER)

        val iverksett = TestData.enIverksettDto()

        val res = httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(iverksett)
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, res.status)
        assertEquals("Iverksetting er skrudd av for fagsystem ${Fagsystem.DAGPENGER}", res.bodyAsText())
    }

    @Test
    fun `start iverksetting`() = runTest {
        val dto = TestData.enIverksettDto()

        val res = httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
        }

        assertEquals(HttpStatusCode.Accepted, res.status)
    }

    @Test
    fun `start iverksetting av tilleggsstønader`() = runTest {
        val dto = TestData.enIverksettDto(
            vedtak = TestData.enVedtaksdetaljer(
                utbetalinger = listOf(
                    TestData.enUtbetalingDto(
                        satstype = Satstype.MÅNEDLIG,
                        fom = LocalDate.of(2021, 1, 1),
                        tom = LocalDate.of(2021, 1, 31),
                        stønadsdata = TestData.enTilleggsstønaderStønadsdata()
                    )
                )
            )
        )

        val res = httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
        }

        assertEquals(HttpStatusCode.Accepted, res.status)

        val statusRes = withTimeoutOrNull(3000) {
            suspend fun getStatus(): HttpResponse {
                return httpClient.get("/api/iverksetting/${dto.sakId}/${dto.behandlingId}/${dto.iverksettingId}/status") {
                    bearerAuth(TestRuntime.azure.generateToken())
                    accept(ContentType.Application.Json)
                }
            }

            var res: HttpResponse = getStatus()
            while (res.status != HttpStatusCode.OK) {
                res = getStatus()
                delay(10)
            }
            res
        }

        assertNotNull(runBlocking { statusRes })
        assertEquals(IverksettStatus.SENDT_TIL_OPPDRAG, statusRes!!.body())
    }

    @Test
    fun `start iverksetting av vedtak uten utbetaling`() = runTest {
        val dto =
            TestData.enIverksettDto(
                vedtak = TestData.enVedtaksdetaljer(
                    utbetalinger = emptyList(),
                ),
            )

        val res = httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
        }

//        kjørTasks() TODO: Test at vi plukker opp og kjører task

        assertEquals(HttpStatusCode.Accepted, res.status)

//        restTemplate
//            .exchange<IverksettStatus>(
//                localhostUrl("/api/iverksetting/$sakId/$behandlingId/status"),
//                HttpMethod.GET,
//                HttpEntity(null, headers),
//            ).also {
//                assertEquals(HttpStatus.OK, it.statusCode)
//                assertEquals(IverksettStatus.OK_UTEN_UTBETALING, it.body)
//            }
    }

    @Test
    fun `returnerer beskrivende feilmelding når jackson ikke greier å deserialisere request`() = runTest {
        @Language("JSON")
        val payload =
            """
            {
              "sakId": "1234",
              "behandlingId": "1",
              "personident": "15507600333",
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
                      "stønadstype": "DAGPENGER_ARBEIDSSØKER_ORDINÆR"
                    }
                  }
                ]
              }
            }
            """.trimIndent()

        val body = objectMapper.readValue<JsonNode>(payload)

        val res = httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals("Klarte ikke lese request body. Sjekk at du ikke mangler noen felter", res.bodyAsText())
    }
}
