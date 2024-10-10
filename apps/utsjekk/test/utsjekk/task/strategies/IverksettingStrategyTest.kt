package utsjekk.task.strategies

import TestData
import TestRuntime
import awaitDatabase
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction
import libs.task.TaskDao
import libs.task.Tasks
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.iverksett.IverksettStatus
import no.nav.utsjekk.kontrakter.iverksett.StatusEndretMelding
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.OppdragResultat
import utsjekk.iverksetting.behandlingId
import utsjekk.iverksetting.iverksettingId
import utsjekk.iverksetting.resultat.IverksettingResultater
import utsjekk.iverksetting.sakId
import java.time.LocalDate

class IverksettingStrategyTest {

    @Test
    fun `uten forrige resultat`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting(
            andelsdatoer = listOf(
                LocalDate.of(2024, 1, 1) to LocalDate.of(2024, 1, 31),
            )
        )
        IverksettingResultater.opprett(iverksetting, resultat = null)

        val oppdragIdDto = TestData.dto.oppdragId(iverksetting)

        TestRuntime.oppdrag.iverksettRespondWith(oppdragIdDto, HttpStatusCode.Created)
        TestRuntime.kafka.expect(iverksetting.søker.personident)

        Tasks.create(libs.task.Kind.Iverksetting, iverksetting, null, objectMapper::writeValueAsString)

        val expectedRecord = StatusEndretMelding(
            sakId = iverksetting.sakId.id,
            behandlingId = iverksetting.behandlingId.id,
            iverksettingId = iverksetting.iverksettingId?.id,
            fagsystem = Fagsystem.DAGPENGER,
            status = IverksettStatus.SENDT_TIL_OPPDRAG,
        )

        assertEquals(expectedRecord, TestRuntime.kafka.waitFor(iverksetting.søker.personident))

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

        TestRuntime.oppdrag.iverksettRespondWith(oppdragIdDto, HttpStatusCode.Created)
        TestRuntime.kafka.expect(iverksetting.søker.personident)

        Tasks.create(libs.task.Kind.Iverksetting, iverksetting, null, objectMapper::writeValueAsString)

        val expectedRecord = StatusEndretMelding(
            sakId = iverksetting.sakId.id,
            behandlingId = iverksetting.behandlingId.id,
            iverksettingId = iverksetting.iverksettingId?.id,
            fagsystem = Fagsystem.DAGPENGER,
            status = IverksettStatus.SENDT_TIL_OPPDRAG,
        )
        assertEquals(expectedRecord, TestRuntime.kafka.waitFor(iverksetting.søker.personident))

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
        TestRuntime.oppdrag.iverksettRespondWith(oppdragIdDto, HttpStatusCode.Created)
        val taskId = Tasks.create(libs.task.Kind.Iverksetting, iverksetting, null, objectMapper::writeValueAsString)

        val actual = awaitDatabase {
            TaskDao.select {
                it.id = taskId
                it.status = listOf(libs.task.Status.FAIL)
            }.firstOrNull()
        }

        assertTrue(actual!!.message!!.contains("Fant ikke forrige iverksettingresultat"))

        transaction {
            actual.copy(status = libs.task.Status.COMPLETE).update()
        }
    }

    @Test
    fun `uten utbetalingsperioder`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        IverksettingResultater.opprett(iverksetting, resultat = null)
        TestRuntime.kafka.expect(iverksetting.søker.personident)
        Tasks.create(libs.task.Kind.Iverksetting, iverksetting, null, objectMapper::writeValueAsString)

        val expectedRecord = StatusEndretMelding(
            sakId = iverksetting.sakId.id,
            behandlingId = iverksetting.behandlingId.id,
            iverksettingId = iverksetting.iverksettingId?.id,
            fagsystem = Fagsystem.DAGPENGER,
            status = IverksettStatus.OK_UTEN_UTBETALING,
        )
        assertEquals(expectedRecord, TestRuntime.kafka.waitFor(iverksetting.søker.personident))

        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(OppdragStatus.OK_UTEN_UTBETALING, resultat.oppdragResultat?.oppdragStatus)
    }
}
