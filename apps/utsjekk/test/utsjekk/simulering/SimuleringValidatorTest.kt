package utsjekk.simulering

import TestData.domain.iverksetting
import TestData.dto.api.forrigeIverksetting
import TestData.dto.api.simuleringRequest
import TestData.dto.api.utbetaling
import TestRuntime
import fakes.Azp
import httpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import no.nav.utsjekk.kontrakter.felles.StønadTypeDagpenger
import no.nav.utsjekk.kontrakter.iverksett.StønadsdataDagpengerDto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.*
import java.time.LocalDateTime

class SimuleringValidatorTest {
    @Test
    fun `skal få BAD_REQUEST når forrige iverksetting har annen behandlingId enn siste mottatte iverksetting`() =
        runTest(TestRuntime.context) {
            val sakId = SakId(RandomOSURId.generate())
            val iverksetting1 = iverksetting(sakId = sakId, iverksettingId = IverksettingId("1"))
            val iverksetting2 = iverksetting(
                sakId = sakId,
                forrigeBehandlingId = iverksetting1.behandlingId,
                forrigeIverksettingId = iverksetting1.iverksettingId,
            )
            transaction {
                IverksettingDao(iverksetting1, LocalDateTime.now().minusDays(2)).insert()
                IverksettingDao(iverksetting2, LocalDateTime.now()).insert()
            }
            IverksettingResultater.opprett(iverksetting1, OppdragResultat(OppdragStatus.KVITTERT_OK))
            IverksettingResultater.oppdater(iverksetting1, iverksetting1.vedtak.tilkjentYtelse)

            val res = httpClient.post("/api/simulering/v2") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken(Azp.DAGPENGER))
                setBody(
                    simuleringRequest(
                        sakId = sakId,
                        utbetalinger = listOf(
                            utbetaling(
                                stønadsdata = StønadsdataDagpengerDto(StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR)
                            )
                        ),
                        forrigeIverksetting = forrigeIverksetting(
                            iverksetting1.behandlingId,
                            iverksetting1.iverksettingId
                        ),
                    ),
                )
            }

            val actual = res.bodyAsText()
            val expected = "Forrige iverksetting stemmer ikke med siste mottatte iverksetting på saken."
            assertTrue(actual.contains(expected)) {
                """
                    Expected: $expected
                    Actual:   $actual
                """.trimIndent()
            }
            assertEquals(HttpStatusCode.BadRequest, res.status)
        }

    @Test
    fun `skal få BAD_REQUEST når forrige iverksetting har annen iverksettingId enn siste mottatte iverksetting`() =
        runTest(TestRuntime.context) {
            val sakId = SakId(RandomOSURId.generate())
            val iverksetting1 = iverksetting(sakId = sakId, iverksettingId = IverksettingId("1"))
            val iverksetting2 = iverksetting(
                sakId = sakId,
                forrigeBehandlingId = iverksetting1.behandlingId,
                forrigeIverksettingId = IverksettingId("abc"),
            )
            transaction {
                IverksettingDao(iverksetting1, LocalDateTime.now().minusDays(2)).insert()
                IverksettingDao(iverksetting2, LocalDateTime.now()).insert()
            }
            IverksettingResultater.opprett(iverksetting1, OppdragResultat(OppdragStatus.KVITTERT_OK))
            IverksettingResultater.oppdater(iverksetting1, iverksetting1.vedtak.tilkjentYtelse)

            val res = httpClient.post("/api/simulering/v2") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken(Azp.DAGPENGER))
                setBody(
                    simuleringRequest(
                        sakId = sakId,
                        utbetalinger = listOf(
                            utbetaling(
                                stønadsdata = StønadsdataDagpengerDto(StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR)
                            )
                        ),
                        forrigeIverksetting = forrigeIverksetting(
                            iverksetting1.behandlingId,
                            iverksetting1.iverksettingId
                        ),
                    ),
                )
            }

            val actual = res.bodyAsText()
            val expected = "Forrige iverksetting stemmer ikke med siste mottatte iverksetting på saken."
            assertTrue(actual.contains(expected)) {
                """
                    Expected: $expected
                    Actual:   $actual
                """.trimIndent()
            }
            assertEquals(HttpStatusCode.BadRequest, res.status)
        }

    @Test
    fun `skal få BAD_REQUEST når forrige iverksetting ikke er satt og vi har mottatt iverksetting på saken tidligere`() =
        runTest(TestRuntime.context) {
            val sakId = SakId(RandomOSURId.generate())
            val iverksetting1 = iverksetting(sakId = sakId, iverksettingId = IverksettingId("1"))
            val iverksetting2 = iverksetting(
                sakId = sakId,
                forrigeBehandlingId = iverksetting1.behandlingId,
                forrigeIverksettingId = iverksetting1.iverksettingId,
            )
            transaction {
                IverksettingDao(iverksetting1, LocalDateTime.now().minusDays(2)).insert()
                IverksettingDao(iverksetting2, LocalDateTime.now()).insert()
            }
            IverksettingResultater.opprett(iverksetting1, OppdragResultat(OppdragStatus.KVITTERT_OK))
            IverksettingResultater.oppdater(iverksetting1, iverksetting1.vedtak.tilkjentYtelse)

            val res = httpClient.post("/api/simulering/v2") {
                contentType(ContentType.Application.Json)
                bearerAuth(TestRuntime.azure.generateToken(Azp.DAGPENGER))
                setBody(
                    simuleringRequest(
                        sakId = sakId,
                        utbetalinger = listOf(
                            utbetaling(
                                stønadsdata = StønadsdataDagpengerDto(StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR)
                            )
                        ),
//                        forrigeIverksetting = forrigeIverksetting(
//                            iverksetting1.behandlingId,
//                            iverksetting1.iverksettingId
//                        ),
                    ),
                )
            }

            val actual = res.bodyAsText()
            val expected = "Forrige iverksetting stemmer ikke med siste mottatte iverksetting på saken."
            assertTrue(actual.contains(expected)) {
                """
                    Expected: $expected
                    Actual:   $actual
                """.trimIndent()
            }
            assertEquals(HttpStatusCode.BadRequest, res.status)
        }

    @Test
    fun `skal få CONFLICT når forrige iverksetting ikke er ferdig og OK mot oppdrag`() = runTest(TestRuntime.context) {
        val sakId = SakId(RandomOSURId.generate())
        val iverksetting1 = iverksetting(sakId = sakId, iverksettingId = IverksettingId("1"))
        val iverksetting2 = iverksetting(
            sakId = sakId,
            forrigeBehandlingId = iverksetting1.behandlingId,
            forrigeIverksettingId = iverksetting1.iverksettingId,
        )
        transaction {
            IverksettingDao(iverksetting1, LocalDateTime.now().minusDays(2)).insert()
            IverksettingDao(iverksetting2, LocalDateTime.now()).insert()
        }

        val res = httpClient.post("/api/simulering/v2") {
            contentType(ContentType.Application.Json)
            bearerAuth(TestRuntime.azure.generateToken(Azp.DAGPENGER))
            setBody(
                simuleringRequest(
                    sakId = sakId,
                    utbetalinger = listOf(
                        utbetaling(
                            stønadsdata = StønadsdataDagpengerDto(StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR)
                        )
                    ),
                    forrigeIverksetting = forrigeIverksetting(
                        iverksetting1.behandlingId,
                        iverksetting1.iverksettingId
                    ),
                ),
            )
        }

        val actual = res.bodyAsText()
        val expected = "Forrige iverksetting er ikke ferdig iverksatt mot Oppdragssystemet"
        assertTrue(actual.contains(expected)) {
            """
                Expected: $expected
                Actual:   $actual
            """.trimIndent()
        }
        assertEquals(HttpStatusCode.Conflict, res.status)
    }
}
