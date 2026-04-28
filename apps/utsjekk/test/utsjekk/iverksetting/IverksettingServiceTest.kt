package utsjekk.iverksetting

import TestData
import TestRuntime
import kotlinx.coroutines.test.runTest
import libs.jdbc.concurrency.transaction
import models.ApiError
import utsjekk.utbetaling.UtbetalingId
import utsjekk.iverksetting.UtbetalingId as IverksettingUtbetalingId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class IverksettingServiceTest {

    @Test
    fun `hent gir internal server error naer resultat mangler`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()

        val err = assertThrows<ApiError> {
            transaction {
                IverksettingService.hent(iverksetting)
            }
        }

        assertEquals(500, err.statusCode)
        assertTrue(err.msg.contains("Fant ikke iverksettingresultat for iverksetting med"))
    }

    @Test
    fun `hent gir internal server error naer utbetaling resultat mangler`() = runTest(TestRuntime.context) {
        val err = assertThrows<ApiError> {
            transaction {
                IverksettingService.hent(IverksettingUtbetalingId(
                    fagsystem = TestData.DEFAULT_FAGSYSTEM,
                    sakId = TestData.domain.iverksetting().sakId,
                    behandlingId = TestData.domain.iverksetting().behandlingId,
                    iverksettingId = TestData.domain.iverksetting().iverksettingId,
                ))
            }
        }

        assertEquals(500, err.statusCode)
        assertTrue(err.msg.contains("Fant ikke iverksettingresultat for iverksetting med"))
    }

    @Test
    fun `hentForrige gir not found naer forrige resultat mangler`() = runTest(TestRuntime.context) {
        val forrige = TestData.domain.iverksetting(
            forrigeBehandlingId = BehandlingId("ikke-brukt"),
            forrigeIverksettingId = IverksettingId("ikke-brukt"),
        )
        val nyeste = TestData.domain.iverksetting(
            sakId = forrige.sakId,
            forrigeBehandlingId = forrige.behandlingId,
            forrigeIverksettingId = forrige.iverksettingId,
        )

        transaction {
            IverksettingDao(forrige, java.time.LocalDateTime.now()).insert(UtbetalingId(UUID.randomUUID()))
        }

        val err = assertThrows<ApiError> {
            transaction {
                IverksettingService.hentForrige(nyeste)
            }
        }

        assertEquals(404, err.statusCode)
        assertTrue(err.msg.contains("Fant ikke forrige iverksettingresultat for iverksetting med"))
    }
}
