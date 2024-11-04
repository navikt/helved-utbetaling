package utsjekk.task.strategies

import TestData
import TestData.domain.enAndelTilkjentYtelse
import TestData.domain.vedtaksdetaljer
import TestRuntime
import awaitDatabase
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import libs.task.TaskDao
import libs.task.Tasks
import no.nav.utsjekk.kontrakter.felles.BrukersNavKontor
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import no.nav.utsjekk.kontrakter.felles.StønadTypeTiltakspenger
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.iverksett.IverksettStatus
import no.nav.utsjekk.kontrakter.iverksett.StatusEndretMelding
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.OppdragResultat
import utsjekk.iverksetting.StønadsdataTiltakspenger
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

        val oppdragResultat = awaitDatabase {
           IverksettingResultater.hent(iverksetting).oppdragResultat
        }
        assertEquals(OppdragStatus.OK_UTEN_UTBETALING, oppdragResultat?.oppdragStatus)
    }

    @Test
    fun `med brukers NAV-kontor`() = runTest(TestRuntime.context) {
        val førsteNavKontor = "4401"
        val sisteNavKontor = "3220"
        val iverksetting = TestData.domain.iverksetting(fagsystem = Fagsystem.TILTAKSPENGER).copy(
            vedtak = vedtaksdetaljer(
                andeler = listOf(
                    enAndelTilkjentYtelse(
                        fom = LocalDate.of(2024, 2, 1),
                        tom = LocalDate.of(2024, 2, 28),
                        stønadsdata = StønadsdataTiltakspenger(stønadstype = StønadTypeTiltakspenger.JOBBKLUBB, brukersNavKontor = BrukersNavKontor(førsteNavKontor), meldekortId = "M1")
                    ),
                    enAndelTilkjentYtelse(
                        fom = LocalDate.of(2024, 4, 1),
                        tom = LocalDate.of(2024, 4, 15),
                        stønadsdata = StønadsdataTiltakspenger(stønadstype = StønadTypeTiltakspenger.JOBBKLUBB, brukersNavKontor = BrukersNavKontor(sisteNavKontor), meldekortId = "M1")
                    )
                )
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
            fagsystem = iverksetting.fagsak.fagsystem,
            status = IverksettStatus.SENDT_TIL_OPPDRAG,
        )
        assertEquals(expectedRecord, TestRuntime.kafka.waitFor(iverksetting.søker.personident))

        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(sisteNavKontor, resultat.tilkjentYtelseForUtbetaling?.utbetalingsoppdrag?.brukersNavKontor)

    }
}
