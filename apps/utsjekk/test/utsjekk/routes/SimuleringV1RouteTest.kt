package utsjekk.simulering

import TestData.domain.iverksetting
import TestData.dto.api.forrigeIverksetting
import TestData.dto.api.simuleringRequest
import TestData.dto.api.utbetaling
import TestRuntime
import fakes.Azp
import httpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import models.kontrakter.felles.Fagsystem
import models.kontrakter.felles.StønadTypeDagpenger
import models.kontrakter.felles.StønadTypeTiltakspenger
import models.kontrakter.felles.objectMapper
import models.kontrakter.iverksett.StønadsdataDagpengerDto
import models.kontrakter.iverksett.StønadsdataTiltakspengerV2Dto
import models.kontrakter.oppdrag.OppdragStatus
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.BehandlingId
import utsjekk.iverksetting.IverksettingDao
import utsjekk.iverksetting.IverksettingId
import utsjekk.iverksetting.OppdragResultat
import utsjekk.iverksetting.RandomOSURId
import utsjekk.iverksetting.SakId
import utsjekk.iverksetting.resultat.IverksettingResultater
import utsjekk.utbetaling.UtbetalingId
import java.time.LocalDateTime
import java.util.UUID

class SimuleringRouteTest {
    @Test
    fun `400 hvis request body ikke kan deserialiseres`() =
        runTest {
            @Language("JSON")
            val jsonUtenBehandlingId =
                """
                {
                    "sakId": "oqjwebco",
                    "personident": "17856299015",
                    "saksbehandlerId": "A123456",
                    "utbetalinger": [
                        {
                            "beløp": 1000,
                            "satstype": "DAGLIG",
                            "fraOgMedDato": "2024-08-01",
                            "tilOgMedDato": "2024-08-31",
                            "stønadsdata": {
                                "stønadstype": "DAGPENGER_ARBEIDSSØKER_ORDINÆR",
                                "meldekortId": "test"
                            }
                        }
                    ]
                }
                """.trimIndent()
            val res =
                httpClient.post("/api/simulering/v2") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.TILLEGGSSTØNADER))
                    setBody(objectMapper.writeValueAsString(jsonUtenBehandlingId))
                }

            assertEquals(HttpStatusCode.BadRequest, res.status)
        }

    @Test
    fun `409 hvis forrige iverksettingresultat mangler`() =
        runTest {
            val sakId = SakId(RandomOSURId.generate())
            val behId = BehandlingId("noe-tull")
            val res =
                httpClient.post("/api/simulering/v2") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.TILLEGGSSTØNADER))
                    setBody(
                        simuleringRequest(
                            sakId = sakId,
                            behandlingId = behId,
                            utbetalinger = listOf(utbetaling()),
                            forrigeIverksetting =
                                forrigeIverksetting(
                                    behandlingId = behId,
                                    iverksettingId = IverksettingId("noe-tull"),
                                ),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Conflict, res.status)
        }

    @Test
    fun `204 ved simulering av tom utbetaling som ikke er opphør`() =
        runTest {
            val sakId = SakId(RandomOSURId.generate())
            val behId = BehandlingId("noe-tull")
            val res =
                httpClient.post("/api/simulering/v2") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(TestRuntime.azure.generateToken())
                    setBody(
                        simuleringRequest(
                            sakId = sakId,
                            behandlingId = behId,
                            utbetalinger = emptyList(),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.NoContent, res.status)
        }

    @Test
    fun `simuler for tiltakspenger`() =
        runTest {
            val sakId = SakId(RandomOSURId.generate())
            val behId = BehandlingId("noe-tull")
            val res =
                httpClient.post("/api/simulering/v2") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.TILTAKSPENGER))
                    setBody(
                        simuleringRequest(
                            sakId = sakId,
                            behandlingId = behId,
                            utbetalinger =
                                listOf(
                                    utbetaling(
                                        stønadsdata =
                                            StønadsdataTiltakspengerV2Dto(
                                                stønadstype = StønadTypeTiltakspenger.ARBEIDSTRENING,
                                                barnetillegg = false,
                                                brukersNavKontor = "4400",
                                                meldekortId = "M1",
                                            ),
                                    ),
                                ),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.OK, res.status)
        }

    @Test
    fun `simuler for tilleggsstønader med eksisterende iverksetting`() =
        runTest(TestRuntime.context) {
            val forrigeIverksettingId = IverksettingId(UUID.randomUUID().toString())
            val forrigeBehandlingId = BehandlingId("forrige-beh")
            val sakId = SakId("en-sakid")
            val iverksetting =
                iverksetting(
                    sakId = sakId,
                    fagsystem = Fagsystem.TILLEGGSSTØNADER,
                    behandlingId = forrigeBehandlingId,
                    iverksettingId = forrigeIverksettingId,
                )

            transaction {
                IverksettingDao(iverksetting, LocalDateTime.now()).insert(UtbetalingId(UUID.randomUUID()))
            }

            IverksettingResultater.opprett(iverksetting, UtbetalingId(UUID.randomUUID()), OppdragResultat(OppdragStatus.KVITTERT_OK))
            IverksettingResultater.oppdater(iverksetting, iverksetting.vedtak.tilkjentYtelse)

            val res =
                httpClient.post("/api/simulering/v2") {
                    contentType(ContentType.Application.Json)
                    bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.TILLEGGSSTØNADER))
                    setBody(
                        simuleringRequest(
                            sakId = sakId,
                            forrigeIverksetting =
                                forrigeIverksetting(
                                    behandlingId = forrigeBehandlingId,
                                    iverksettingId = forrigeIverksettingId,
                                ),
                            utbetalinger =
                                listOf(
                                    utbetaling(
                                        stønadsdata =
                                            StønadsdataDagpengerDto(
                                                stønadstype = StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR,
                                                meldekortId = "M1",
                                            fastsattDagsats = 1000u,
                            ),
                                    ),
                                ),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.OK, res.status)
        }
}
