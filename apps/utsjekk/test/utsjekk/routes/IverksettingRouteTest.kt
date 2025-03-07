package utsjekk.routes

import TestData
import TestRuntime
import awaitDatabase
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
import libs.postgres.concurrency.transaction
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
import utsjekk.iverksetting.BehandlingId
import utsjekk.iverksetting.IverksettingDao
import utsjekk.iverksetting.IverksettingId
import utsjekk.iverksetting.SakId
import utsjekk.iverksetting.behandlingId
import utsjekk.iverksetting.resultat.IverksettingResultatDao
import java.time.LocalDate
import utsjekk.DEFAULT_DOC_STR

class IverksettingRouteTest {
    @BeforeEach
    fun reset() {
        TestRuntime.unleash.reset()
    }

    @Test
    fun `start iverksetting av vedtak uten utbetaling`() = runTest(TestRuntime.context) {
        withContext(TestRuntime.context) {
            val dto = TestData.dto.iverksetting(
                vedtak = TestData.dto.vedtaksdetaljer(
                    utbetalinger = emptyList(),
                ),
            )
            TestRuntime.kafka.expect(dto.personident.verdi)
            httpClient.post("/api/iverksetting/v2") {
                bearerAuth(TestRuntime.azure.generateToken())
                contentType(ContentType.Application.Json)
                setBody(dto)
            }.let {
                assertEquals(HttpStatusCode.Accepted, it.status)
            }

            val status = runBlocking {
                awaitDatabase {
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

            val expectedRecord = StatusEndretMelding(
                sakId = dto.sakId,
                behandlingId = dto.behandlingId,
                iverksettingId = dto.iverksettingId,
                fagsystem = Fagsystem.TILLEGGSSTØNADER,
                status = IverksettStatus.OK_UTEN_UTBETALING,
            )

            assertEquals(expectedRecord, TestRuntime.kafka.waitFor(dto.personident.verdi))
        }
    }

    @Test
    fun `iverksetter ikke når kill switch for ytelsen er skrudd på`() = runTest(TestRuntime.context) {
        TestRuntime.unleash.disable(Fagsystem.TILLEGGSSTØNADER)

        val dto = TestData.dto.iverksetting()

        val res = httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, res.status)
        assertEquals("""{"msg":"Iverksetting er skrudd av for fagsystem TILLEGGSSTØNADER","field":null,"doc":"$DEFAULT_DOC_STR"}""", res.bodyAsText())
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

        awaitDatabase {
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
        assertEquals("""{"msg":"Klarte ikke lese request body. Sjekk at du ikke mangler noen felter","field":null,"doc":"${DEFAULT_DOC_STR}"}""", res.bodyAsText())
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

            awaitDatabase {
                IverksettingDao.select {
                    this.fagsystem = Fagsystem.TILLEGGSSTØNADER
                    this.sakId = SakId(dto.sakId)
                    this.behandlingId = BehandlingId(dto.behandlingId)
                }.firstOrNull()
            }.also {
                requireNotNull(it) { "iverksetting not found i db" }
            }

            awaitDatabase {
                IverksettingResultatDao.select {
                    this.fagsystem = Fagsystem.TILLEGGSSTØNADER
                    this.sakId = SakId(dto.sakId)
                    this.behandlingId = BehandlingId(dto.behandlingId)
                }.find {
                    it.oppdragResultat?.oppdragStatus == OppdragStatus.KVITTERT_OK
                }
            }.also {
                requireNotNull(it) { "iverksetting resultat not found i db" }
            }

            val status = httpClient.get("/api/iverksetting/${dto.sakId}/${dto.behandlingId}/status") {
                bearerAuth(TestRuntime.azure.generateToken())
                accept(ContentType.Application.Json)
            }.body<IverksettStatus>()

            assertEquals(IverksettStatus.OK, status)
        }
    }

    @Test
    fun `svarer med CONFLICT når iverksetting allerede er iverksatt`() = runTest(TestRuntime.context) {
        val dto = TestData.dto.iverksetting()
        val iverksetting = TestData.domain.iverksetting(
            fagsystem = Fagsystem.TILLEGGSSTØNADER,
            sakId = SakId(dto.sakId),
            behandlingId = BehandlingId(dto.behandlingId),
        )

        transaction {
            TestData.dao.iverksetting(behandlingId = iverksetting.behandlingId, iverksetting = iverksetting).also {
                it.insert()
            }
        }

        val res = httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
        }

        assertEquals(HttpStatusCode.Conflict, res.status)
    }
}
