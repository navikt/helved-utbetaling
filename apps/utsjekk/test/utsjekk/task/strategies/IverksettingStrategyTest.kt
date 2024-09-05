package utsjekk.task.strategies

import TestData
import TestRuntime
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.IverksettingDao
import utsjekk.iverksetting.IverksettingResultater
import utsjekk.iverksetting.behandlingId
import utsjekk.task.Kind
import utsjekk.task.Tasks
import java.time.LocalDateTime

class IverksettingStrategyTest {

    @AfterEach
    fun reset() {
        TestRuntime.oppdrag.reset()
    }

    @Test
    @Disabled
    fun `kan iverksette oppdrag med utbetalingsperioder`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        transaction {
            IverksettingDao(iverksetting.behandlingId, iverksetting, LocalDateTime.now()).insert()
        }
        IverksettingResultater.opprett(iverksetting, resultat = null)

        val oppdragIdDto = TestData.dto.oppdragId(iverksetting)
        TestRuntime.oppdrag.iverksettRespondWith(oppdragIdDto, HttpStatusCode.OK)
        Tasks.create(Kind.Iverksetting, iverksetting)

        TestRuntime.oppdrag.awaitIverksett(oppdragIdDto)

        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(OppdragStatus.LAGT_PÅ_KØ, resultat.oppdragResultat?.oppdragStatus)

        assertTrue(Tasks.forKind(Kind.SjekkStatus).any {
            oppdragIdDto == objectMapper.readValue(it.payload)
        })
    }

    @Test
    fun `uten forrige resultat`() = runTest(TestRuntime.context) {

    }

    @Test
    fun `med forrige resultat`() = runTest(TestRuntime.context) {

    }

    @Test
    fun `uten utbetalingsperioder`() = runTest(TestRuntime.context) {

    }

    @Test
    fun `med utbetalingsperioder`() = runTest(TestRuntime.context) {

    }
}