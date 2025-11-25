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
import libs.jdbc.concurrency.transaction
import libs.jdbc.*
import models.DocumentedErrors
import models.Status
import models.StatusReply
import models.kontrakter.felles.Fagsystem
import models.kontrakter.felles.Satstype
import models.kontrakter.felles.objectMapper
import models.kontrakter.iverksett.IverksettStatus
import models.kontrakter.oppdrag.OppdragIdDto
import models.kontrakter.oppdrag.OppdragStatus
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.BehandlingId
import utsjekk.iverksetting.IverksettingDao
import utsjekk.iverksetting.IverksettingId
import utsjekk.iverksetting.SakId
import utsjekk.iverksetting.resultat.IverksettingResultatDao
import java.time.LocalDate

class IverksettingRouteTest {

    @Test
    fun `start iverksetting av vedtak uten utbetaling`() = runTest(TestRuntime.context) {
        withContext(TestRuntime.context) {
            val dto = TestData.dto.iverksetting(
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
                TestRuntime.jdbc.await {
                    IverksettingResultatDao.select {
                        this.fagsystem = Fagsystem.TILLEGGSSTØNADER
                        this.sakId = SakId(dto.sakId)
                        this.behandlingId = BehandlingId(dto.behandlingId)
                    }.firstOrNull {
                        it.oppdragResultat != null
                    }
                }

                httpClient.get("/api/iverksetting/${dto.sakId}/${dto.behandlingId}/status") {
                    bearerAuth(TestRuntime.azure.generateToken())
                    accept(ContentType.Application.Json)
                }.body<IverksettStatus>()
            }

            assertEquals(IverksettStatus.OK_UTEN_UTBETALING, status)
        }
    }

    @Test
    fun `start iverksetting`() = runTest(TestRuntime.context) {
        val dto = TestData.dto.iverksetting()
        val res = httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
        }
        assertEquals(HttpStatusCode.Accepted, res.status)
    }

    @Test
    fun `start iverksetting av tilleggsstønader`() = runTest(TestRuntime.context) {
        val dto = TestData.dto.iverksetting(
            iverksettingId = "en-iverksetting",
            vedtak = TestData.dto.vedtaksdetaljer(
                utbetalinger = listOf(
                    TestData.dto.utbetaling(
                        satstype = Satstype.MÅNEDLIG,
                        fom = LocalDate.of(2021, 1, 1),
                        tom = LocalDate.of(2021, 1, 31),
                        stønadsdata = TestData.dto.tilleggstønad(),
                    ),
                ),
            ),
        )

        val oppdragId = OppdragIdDto(
            fagsystem = Fagsystem.TILLEGGSSTØNADER,
            sakId = dto.sakId,
            behandlingId = dto.behandlingId,
            iverksettingId = dto.iverksettingId,
        )

        TestRuntime.oppdrag.iverksettRespondWith(oppdragId, HttpStatusCode.Created)

        httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
        }.let {
            assertEquals(HttpStatusCode.Accepted, it.status)
        }

        TestRuntime.jdbc.await {
            IverksettingResultatDao.select {
                this.fagsystem = Fagsystem.TILLEGGSSTØNADER
                this.sakId = SakId(dto.sakId)
                this.behandlingId = BehandlingId(dto.behandlingId)
                this.iverksettingId = IverksettingId(dto.iverksettingId!!)
            }.firstOrNull {
                it.oppdragResultat != null
            }
        }

        val status = httpClient.get("/api/iverksetting/${dto.sakId}/${dto.behandlingId}/${dto.iverksettingId}/status") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<IverksettStatus>()

        assertEquals(IverksettStatus.SENDT_TIL_OPPDRAG, status)
    }


    @Test
    fun `returnerer beskrivende feilmelding når jackson ikke greier å deserialisere request`() = runTest {
        @Language("JSON") val payload = """
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
        assertEquals(
            """{"statusCode":400,"msg":"Klarte ikke lese request body. Sjekk at du ikke mangler noen felter","doc":"${DocumentedErrors.BASE}/async/kom_i_gang/opprett_utbetaling","system":"HELVED","suppressed":[]}""",
            res.bodyAsText()
        )
    }

    @Test
    fun `iverksetting blir kvittert ok`() = runTest(TestRuntime.context) {
        val dto = TestData.dto.iverksetting()
        val res = httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
        }

        assertEquals(HttpStatusCode.Accepted, res.status)

        TestRuntime.jdbc.await {
            IverksettingDao.select {
                this.fagsystem = Fagsystem.TILLEGGSSTØNADER
                this.sakId = SakId(dto.sakId)
                this.behandlingId = BehandlingId(dto.behandlingId)
            }.firstOrNull()
        }.also {
            requireNotNull(it) { "iverksetting not found i db" }
        }

        val uid = transaction {
            IverksettingDao.uid {
                this.fagsystem = Fagsystem.TILLEGGSSTØNADER
                this.sakId = SakId(dto.sakId)
                this.behandlingId = BehandlingId(dto.behandlingId)
            }
        }
        requireNotNull(uid) { "iverksetting.uid was null" }

        TestRuntime.topics.status.produce(uid.id.toString()) {
            StatusReply(Status.OK)
        }

        TestRuntime.jdbc.await {
            IverksettingResultatDao.select {
                this.fagsystem = Fagsystem.TILLEGGSSTØNADER
                this.sakId = SakId(dto.sakId)
                this.behandlingId = BehandlingId(dto.behandlingId)
            }.find {
                it.oppdragResultat?.oppdragStatus == OppdragStatus.KVITTERT_OK
            }
        }.also {
            requireNotNull(it) { "iverksettingsresultat not found i db" }
        }

        val status = httpClient.get("/api/iverksetting/${dto.sakId}/${dto.behandlingId}/status") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<IverksettStatus>()

        assertEquals(IverksettStatus.OK, status)
    }

    @Test
    fun `svarer med ACCEPTED når det egentlig er CONFLICT for å hindre spam`() = runTest(TestRuntime.context) {
        val dto = TestData.dto.iverksetting()

        httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
        }.let {
            println(it.bodyAsText())
            assertEquals(HttpStatusCode.Accepted, it.status)
        }

        httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
        }.let {
            println(it.bodyAsText())
            assertEquals(HttpStatusCode.Accepted, it.status)
        }
    }

    @Test
    fun `setter fagsystem eksplisitt med header`() = runTest(TestRuntime.context) {
        val dto = TestData.dto.iverksetting()

        httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
            header("fagsystem", "AAP")
        }.let {
            println(it.bodyAsText())
            assertEquals(HttpStatusCode.Accepted, it.status)
        }

        val notFound = httpClient.get("/api/iverksetting/${dto.sakId}/${dto.behandlingId}/status") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.NotFound, notFound.status)

        val status = httpClient.get("/api/iverksetting/${dto.sakId}/${dto.behandlingId}/status") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
            header("fagsystem", "AAP")
        }.body<IverksettStatus>()

        assertEquals(IverksettStatus.SENDT_TIL_OPPDRAG, status)
    }
}
