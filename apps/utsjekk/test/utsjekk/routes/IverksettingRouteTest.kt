package utsjekk.routes

import TestData
import TestRuntime
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import httpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.Satstype
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.iverksett.IverksettStatus
import no.nav.utsjekk.kontrakter.iverksett.StatusEndretMelding
import no.nav.utsjekk.kontrakter.oppdrag.OppdragIdDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.IverksettingDao
import utsjekk.iverksetting.resultat.IverksettingResultatDao
import utsjekk.task.TaskDao
import utsjekk.task.history.TaskHistoryDao
import java.time.LocalDate

class IverksettingRouteTest {
    @BeforeEach
    fun reset() {
        TestRuntime.oppdrag.reset()
        TestRuntime.unleash.reset()
        TestRuntime.clear(
            TaskDao.TABLE_NAME,
            TaskHistoryDao.TABLE_NAME,
            IverksettingDao.TABLE_NAME,
            IverksettingResultatDao.TABLE_NAME,
        )
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
        assertEquals(
            "Iverksetting er skrudd av for fagsystem ${Fagsystem.TILLEGGSSTØNADER}",
            res.bodyAsText()
        )
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
        withContext(TestRuntime.context) {
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

            val oppdragId = OppdragIdDto(
                fagsystem = Fagsystem.TILLEGGSSTØNADER,
                sakId = dto.sakId,
                behandlingId = dto.behandlingId,
                iverksettingId = dto.iverksettingId
            )

            TestRuntime.oppdrag.iverksettRespondWith(oppdragId, HttpStatusCode.OK)

            httpClient.post("/api/iverksetting/v2") {
                bearerAuth(TestRuntime.azure.generateToken())
                contentType(ContentType.Application.Json)
                setBody(dto)
            }.let {
                assertEquals(HttpStatusCode.Accepted, it.status)
            }

            val status = runBlocking {
                suspend fun getStatus(attempt: Int): IverksettStatus {
                    val res =
                        httpClient.get("/api/iverksetting/${dto.sakId}/${dto.behandlingId}/${dto.iverksettingId}/status") {
                            bearerAuth(TestRuntime.azure.generateToken())
                            accept(ContentType.Application.Json)
                        }.body<IverksettStatus>()
                    return if (res != IverksettStatus.SENDT_TIL_OPPDRAG) getStatus(attempt + 1)
                    else res
                }
                getStatus(0)
            }

            assertEquals(IverksettStatus.SENDT_TIL_OPPDRAG, status)
        }
    }


    @Test
    fun `start iverksetting av vedtak uten utbetaling`() = runTest {
        withContext(TestRuntime.context) {

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
            val status = runBlocking {
                suspend fun getStatus(attempt: Int): IverksettStatus {
                    val res =
                        httpClient.get("/api/iverksetting/${dto.sakId}/${dto.behandlingId}/status") {
                            bearerAuth(TestRuntime.azure.generateToken())
                            accept(ContentType.Application.Json)
                        }.body<IverksettStatus>()
                    return if (res != IverksettStatus.OK_UTEN_UTBETALING) getStatus(attempt + 1)
                    else res
                }
                getStatus(0)
            }

            assertEquals(IverksettStatus.OK_UTEN_UTBETALING, status)

            val expectedRecord = StatusEndretMelding(
                sakId = dto.sakId,
                behandlingId = dto.behandlingId,
                iverksettingId = dto.iverksettingId,
                fagsystem = Fagsystem.TILLEGGSSTØNADER,
                status = IverksettStatus.OK_UTEN_UTBETALING,
            )

            assertEquals(expectedRecord, TestRuntime.kafka.produced.await())
        }
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

    @Test
    fun `iverksetting blir kvittert ok`() = runTest {
        withContext(TestRuntime.context) {

            val dto = TestData.dto.iverksetting()
            val oppdragId = OppdragIdDto(
                fagsystem = Fagsystem.TILLEGGSSTØNADER,
                sakId = dto.sakId,
                behandlingId = dto.behandlingId,
                iverksettingId = dto.iverksettingId,
            )

            TestRuntime.oppdrag.iverksettRespondWith(oppdragId, HttpStatusCode.Created)
            TestRuntime.oppdrag.statusRespondWith(oppdragId, TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_OK))

            val res = httpClient.post("/api/iverksetting/v2") {
                bearerAuth(TestRuntime.azure.generateToken())
                contentType(ContentType.Application.Json)
                setBody(dto)
            }

            assertEquals(HttpStatusCode.Accepted, res.status)
            val status = runBlocking {
                suspend fun getStatus(attempt: Int): IverksettStatus {
                    val res =
                        httpClient.get("/api/iverksetting/${dto.sakId}/${dto.behandlingId}/status") {
                            bearerAuth(TestRuntime.azure.generateToken())
                            accept(ContentType.Application.Json)
                        }.body<IverksettStatus>()
                    return if (res != IverksettStatus.OK) getStatus(attempt + 1)
                    else res
                }
                getStatus(0)
            }

            assertEquals(IverksettStatus.OK, status)
        }
    }
}