package utsjekk.simulering

import TestData.domain.iverksetting
import TestData.dto.api.forrigeIverksetting
import TestData.dto.api.simuleringRequest
import TestData.dto.api.utbetaling
import TestRuntime
import httpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import no.nav.utsjekk.kontrakter.felles.StønadTypeDagpenger
import no.nav.utsjekk.kontrakter.felles.StønadTypeTiltakspenger
import no.nav.utsjekk.kontrakter.iverksett.StønadsdataDagpengerDto
import no.nav.utsjekk.kontrakter.iverksett.StønadsdataTiltakspengerV2Dto
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.*
import utsjekk.iverksetting.resultat.IverksettingResultater
import java.time.LocalDateTime
import java.util.*

class SimuleringRouteTest {

    @Test
    fun test() {
        println(TestRuntime.config.postgres)
    }

    @Test
    fun `409 hvis forrige iverksettingresultat mangler`() = runTest {
        val sakId = SakId(RandomOSURId.generate())
        val behId = BehandlingId("noe-tull")
        val res = httpClient.post("/api/simulering/v2") {
            contentType(ContentType.Application.Json)
            bearerAuth(TestRuntime.azure.generateToken())
            setBody(
                simuleringRequest(
                    sakId = sakId,
                    behandlingId = behId,
                    utbetalinger = listOf(utbetaling()),
                    forrigeIverksetting = forrigeIverksetting(
                        behandlingId = behId,
                        iverksettingId = IverksettingId("noe-tull")
                    ),
                )
            )
        }

        assertEquals(HttpStatusCode.Conflict, res.status)
    }

    @Test
    fun `204 ved simulering av tom utbetaling som ikke er opphør`() = runTest {
        val sakId = SakId(RandomOSURId.generate())
        val behId = BehandlingId("noe-tull")
        val res = httpClient.post("/api/simulering/v2") {
            contentType(ContentType.Application.Json)
            bearerAuth(TestRuntime.azure.generateToken())
            setBody(
                simuleringRequest(
                    sakId = sakId,
                    behandlingId = behId,
                    utbetalinger = emptyList(),
                )
            )
        }

        assertEquals(HttpStatusCode.NoContent, res.status)
    }

    @Test
    fun `simuler for tiltakspenger`() = runTest {
        val sakId = SakId(RandomOSURId.generate())
        val behId = BehandlingId("noe-tull")
        val res = httpClient.post("/api/simulering/v2") {
            contentType(ContentType.Application.Json)
            bearerAuth(TestRuntime.azure.generateToken())
            setBody(
                simuleringRequest(
                    sakId = sakId,
                    behandlingId = behId,
                    utbetalinger = listOf(
                        utbetaling(
                            stønadsdata = StønadsdataTiltakspengerV2Dto(
                                stønadstype = StønadTypeTiltakspenger.ARBEIDSTRENING,
                                barnetillegg = false,
                                brukersNavKontor = "4400"
                            )
                        )
                    ),
                )
            )
        }

        assertEquals(HttpStatusCode.OK, res.status)
    }

    @Test
    fun `simuler for tilleggsstønader med eksisterende iverksetting`() = runTest(TestRuntime.context) {
        val forrigeIverksettingId = IverksettingId(UUID.randomUUID().toString())
        val forrigeBehandlingId = BehandlingId("forrige-beh")
        val sakId = SakId("en-sakid")
        val iverksetting = iverksetting(
            sakId = sakId,
            behandlingId = forrigeBehandlingId,
            iverksettingId = forrigeIverksettingId,
        )

        transaction {
           IverksettingDao(iverksetting, LocalDateTime.now()).insert()
        }

        IverksettingResultater.opprett(iverksetting, OppdragResultat(OppdragStatus.KVITTERT_OK))
        IverksettingResultater.oppdater(iverksetting, iverksetting.vedtak.tilkjentYtelse)

        val res = httpClient.post("/api/simulering/v2") {
            contentType(ContentType.Application.Json)
            bearerAuth(TestRuntime.azure.generateToken("test:helved:utsjekk"))
            setBody(
                simuleringRequest(
                    sakId = sakId,
                    forrigeIverksetting = forrigeIverksetting(
                        behandlingId = forrigeBehandlingId,
                        iverksettingId = forrigeIverksettingId,
                    ),
                    utbetalinger = listOf(
                        utbetaling(
                            stønadsdata = StønadsdataDagpengerDto(
                               stønadstype = StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR
                            )
                        )
                    ),
                )
            )
        }

        assertEquals(HttpStatusCode.OK, res.status)
    }
}
