package utsjekk.task.strategies

import TestData
import TestRuntime
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.iverksett.IverksettStatus
import no.nav.utsjekk.kontrakter.iverksett.StatusEndretMelding
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import repeatUntil
import utsjekk.iverksetting.*
import utsjekk.iverksetting.resultat.IverksettingResultater
import utsjekk.task.Kind
import utsjekk.task.Status
import utsjekk.task.Tasks
import java.time.LocalDate

class IverksettingStrategyTest {

    @AfterEach
    fun reset() {
        TestRuntime.oppdrag.reset()
        TestRuntime.kafka.reset()
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

        val expectedRecord = StatusEndretMelding(
            sakId = iverksetting.sakId.id,
            behandlingId = iverksetting.behandlingId.id,
            iverksettingId = iverksetting.iverksettingId?.id,
            fagsystem = Fagsystem.DAGPENGER,
            status = IverksettStatus.SENDT_TIL_OPPDRAG,
        )

        assertEquals(expectedRecord, TestRuntime.kafka.produced.await())

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

        val expectedRecord = StatusEndretMelding(
            sakId = iverksetting.sakId.id,
            behandlingId = iverksetting.behandlingId.id,
            iverksettingId = iverksetting.iverksettingId?.id,
            fagsystem = Fagsystem.DAGPENGER,
            status = IverksettStatus.SENDT_TIL_OPPDRAG,
        )

        assertEquals(expectedRecord, TestRuntime.kafka.produced.await())

        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(OppdragStatus.LAGT_PÅ_KØ, resultat.oppdragResultat?.oppdragStatus)

        val utbetaling = TestRuntime.oppdrag.awaitIverksett(oppdragIdDto)
        assertTrue(utbetaling.utbetalingsperiode.any {
            it.opphør != null
        })
    }

    @Test
    fun `kan ikke iverksette hvis vi ikke finner resultat for tidligere iverksetting`() = runTest(TestRuntime.context) {
        val tidligereIverksetting = TestData.domain.tidligereIverksetting(
            andelsdatoer = listOf(
                LocalDate.of(2024, 1, 1) to LocalDate.of(2024, 1, 31),
            )
        )

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

        val task = repeatUntil({ Tasks.forId(taskId) }) {
            it?.status == Status.FAIL
        }

        assertTrue(task!!.message!!.contains("Fant ikke forrige iverksettingresultat"))
    }

    @Test
    fun `uten utbetalingsperioder`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        IverksettingResultater.opprett(iverksetting, resultat = null)

        val taskId = Tasks.create(Kind.Iverksetting, iverksetting)

        repeatUntil({ Tasks.forId(taskId) }) {
            it?.status == Status.COMPLETE
        }

        val expectedRecord = StatusEndretMelding(
            sakId = iverksetting.sakId.id,
            behandlingId = iverksetting.behandlingId.id,
            iverksettingId = iverksetting.iverksettingId?.id,
            fagsystem = Fagsystem.DAGPENGER,
            status = IverksettStatus.OK_UTEN_UTBETALING,
        )

        assertEquals(expectedRecord, TestRuntime.kafka.produced.await())

        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(OppdragStatus.OK_UTEN_UTBETALING, resultat.oppdragResultat?.oppdragStatus)
    }
}