package utsjekk.task.strategies

import TestData
import TestRuntime
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import repeatUntil
import utsjekk.iverksetting.*
import utsjekk.task.Kind
import utsjekk.task.Status
import utsjekk.task.Tasks
import java.time.LocalDate

class IverksettingStrategyTest {

    @AfterEach
    fun reset() {
        TestRuntime.oppdrag.reset()
    }

    @Test
    fun `kan iverksette oppdrag med utbetalingsperioder`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting(
            andelsdatoer = listOf(
                LocalDate.of(2024, 1, 1) to LocalDate.of(2024, 1, 31),
            )
        )
        IverksettingResultater.opprett(iverksetting, resultat = null)

        val oppdragIdDto = TestData.dto.oppdragId(iverksetting)
        TestRuntime.oppdrag.iverksettRespondWith(oppdragIdDto, HttpStatusCode.OK)
        val taskId = Tasks.create(Kind.Iverksetting, iverksetting)

        repeatUntil({ Tasks.forId(taskId) }) {
            it?.status == Status.COMPLETE
        }

        assertTrue(TestRuntime.kafka.hasProduced(iverksetting.søker.personident))

        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(OppdragStatus.LAGT_PÅ_KØ, resultat.oppdragResultat?.oppdragStatus)
    }

    @Test
    fun `uten forrige resultat`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting(
            andelsdatoer = listOf(
                LocalDate.of(2024, 1, 1) to LocalDate.of(2024, 1, 31),
            )
        )
        IverksettingResultater.opprett(iverksetting, resultat = null)

        val oppdragIdDto = TestData.dto.oppdragId(iverksetting)
        TestRuntime.oppdrag.iverksettRespondWith(oppdragIdDto, HttpStatusCode.OK)
        val taskId = Tasks.create(Kind.Iverksetting, iverksetting)

        repeatUntil({ Tasks.forId(taskId) }) {
            it?.status == Status.COMPLETE
        }

        assertTrue(TestRuntime.kafka.hasProduced(iverksetting.søker.personident))

        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(OppdragStatus.LAGT_PÅ_KØ, resultat.oppdragResultat?.oppdragStatus)
    }

    @Test
    fun `med forrige resultat`() = runTest(TestRuntime.context) {
        val tidligereIverksetting = TestData.domain.tidligereIverksetting(
            andelsdatoer = listOf(
                LocalDate.of(2024, 1, 1) to LocalDate.of(2024, 1, 31),
            )
        )
        IverksettingResultater.opprett(tidligereIverksetting, resultat = OppdragResultat(OppdragStatus.KVITTERT_OK))
        IverksettingResultater.oppdater(tidligereIverksetting, tidligereIverksetting.vedtak.tilkjentYtelse)

        val iverksetting = TestData.domain.iverksetting(
            andelsdatoer = listOf(
                LocalDate.of(2024, 2, 1) to LocalDate.of(2024, 2, 28),
            ),
            sakId = tidligereIverksetting.sakId,
            forrigeBehandlingId = tidligereIverksetting.behandlingId,
            forrigeIverksettingId = tidligereIverksetting.iverksettingId,
        )
        IverksettingResultater.opprett(iverksetting, resultat = null)

        val oppdragIdDto = TestData.dto.oppdragId(iverksetting)
        TestRuntime.oppdrag.iverksettRespondWith(oppdragIdDto, HttpStatusCode.OK)
        val taskId = Tasks.create(Kind.Iverksetting, iverksetting)

        repeatUntil({ Tasks.forId(taskId) }) {
            it?.status == Status.COMPLETE
        }

        assertTrue(TestRuntime.kafka.hasProduced(iverksetting.søker.personident))

        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(OppdragStatus.LAGT_PÅ_KØ, resultat.oppdragResultat?.oppdragStatus)

        val utbetaling = TestRuntime.oppdrag.awaitIverksett(oppdragIdDto)
        assertTrue(utbetaling.utbetalingsperiode.any {
            it.opphør != null
        })
    }

    @Test
    fun `uten utbetalingsperioder`() = runTest(TestRuntime.context) {

    }

    @Test
    fun `med utbetalingsperioder`() = runTest(TestRuntime.context) {

    }
}