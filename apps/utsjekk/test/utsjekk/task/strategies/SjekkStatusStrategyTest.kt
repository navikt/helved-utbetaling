package utsjekk.task.strategies

import TestData
import TestRuntime
import kotlinx.coroutines.test.runTest
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import repeatUntil
import utsjekk.iverksetting.IverksettingResultater
import utsjekk.task.Kind
import utsjekk.task.Status
import utsjekk.task.Tasks

class SjekkStatusStrategyTest {

    @AfterEach
    fun reset() {
        TestRuntime.oppdrag.reset()
    }

    @Test
    fun `setter task til COMPLETE når status er KVITTERT_OK`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        val oppdragId = TestData.dto.oppdragId(iverksetting)
        val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_OK)
        IverksettingResultater.opprett(iverksetting, null)
        TestRuntime.oppdrag.respondWith(oppdragStatus, oppdragId)
        val taskId = Tasks.create(Kind.SjekkStatus, oppdragId)

        val task = repeatUntil(
            function = { Tasks.forId(taskId) },
            predicate = { task -> task?.attempt == 1 }
        )

        val actual = TestRuntime.oppdrag.expectedStatusRequestBody.await()
        assertEquals(oppdragId, actual)

        assertEquals("", task?.message)
        assertEquals(1, task?.attempt)
        assertEquals(Status.COMPLETE, task?.status)
        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(OppdragStatus.KVITTERT_OK, resultat.oppdragResultat?.oppdragStatus)
    }

    @Test
    fun `setter task til MANUAL når status er KVITTERT_MED_MANGLER`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        val oppdragId = TestData.dto.oppdragId(iverksetting)
        val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_MED_MANGLER, "mangelvare")
        IverksettingResultater.opprett(iverksetting, null)
        TestRuntime.oppdrag.respondWith(oppdragStatus, oppdragId)
        val taskId = Tasks.create(Kind.SjekkStatus, oppdragId)

        val task = repeatUntil(
            function = { Tasks.forId(taskId) },
            predicate = { task -> task?.attempt == 1 }
        )

        val actual = TestRuntime.oppdrag.expectedStatusRequestBody.await()
        assertEquals(oppdragId, actual)

        assertEquals("mangelvare", task?.message)
        assertEquals(1, task?.attempt)
        assertEquals(Status.MANUAL, task?.status)
        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(OppdragStatus.KVITTERT_MED_MANGLER, resultat.oppdragResultat?.oppdragStatus)
    }

    @Test
    fun `setter task til MANUAL når status er KVITTERT_TEKNISK_FEIL`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        val oppdragId = TestData.dto.oppdragId(iverksetting)
        val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_TEKNISK_FEIL, "teknisk")
        IverksettingResultater.opprett(iverksetting, null)
        TestRuntime.oppdrag.respondWith(oppdragStatus, oppdragId)
        val taskId = Tasks.create(Kind.SjekkStatus, oppdragId)

        val task = repeatUntil(
            function = { Tasks.forId(taskId) },
            predicate = { task -> task?.attempt == 1 }
        )

        val actual = TestRuntime.oppdrag.expectedStatusRequestBody.await()
        assertEquals(oppdragId, actual)

        assertEquals("teknisk", task?.message)
        assertEquals(1, task?.attempt)
        assertEquals(Status.MANUAL, task?.status)
        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(OppdragStatus.KVITTERT_TEKNISK_FEIL, resultat.oppdragResultat?.oppdragStatus)
    }

    @Test
    fun `setter task til MANUAL når status er KVITTERT_FUNKSJONELL_FEIL`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        val oppdragId = TestData.dto.oppdragId(iverksetting)
        val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_FUNKSJONELL_FEIL, "funkis")
        IverksettingResultater.opprett(iverksetting, null)
        TestRuntime.oppdrag.respondWith(oppdragStatus, oppdragId)
        val taskId = Tasks.create(Kind.SjekkStatus, oppdragId)

        val task = repeatUntil(
            function = { Tasks.forId(taskId) },
            predicate = { task -> task?.attempt == 1 }
        )

        val actual = TestRuntime.oppdrag.expectedStatusRequestBody.await()
        assertEquals(oppdragId, actual)

        assertEquals("funkis", task?.message)
        assertEquals(1, task?.attempt)
        assertEquals(Status.MANUAL, task?.status)
        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(OppdragStatus.KVITTERT_FUNKSJONELL_FEIL, resultat.oppdragResultat?.oppdragStatus)
    }

    @Test
    fun `setter task til MANUAL når status er KVITTERT_UKJENT`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        val oppdragId = TestData.dto.oppdragId(iverksetting)
        val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_UKJENT)
        IverksettingResultater.opprett(iverksetting, null)
        TestRuntime.oppdrag.respondWith(oppdragStatus, oppdragId)
        val taskId = Tasks.create(Kind.SjekkStatus, oppdragId)

        val task = repeatUntil(
            function = { Tasks.forId(taskId) },
            predicate = { task -> task?.attempt == 1 }
        )

        val actual = TestRuntime.oppdrag.expectedStatusRequestBody.await()
        assertEquals(oppdragId, actual)

        assertEquals("Ukjent kvittering fra OS", task?.message)
        assertEquals(1, task?.attempt)
        assertEquals(Status.MANUAL, task?.status)
        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(OppdragStatus.KVITTERT_UKJENT, resultat.oppdragResultat?.oppdragStatus)
    }

    @Test
    fun `oppdaterer antall forsøk når status er LAGT_PÅ_KØ`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        val oppdragId = TestData.dto.oppdragId(iverksetting)
        val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.LAGT_PÅ_KØ)
        IverksettingResultater.opprett(iverksetting, null)
        TestRuntime.oppdrag.respondWith(oppdragStatus, oppdragId)
        val taskId = Tasks.create(Kind.SjekkStatus, oppdragId)

        val task = repeatUntil(
            function = { Tasks.forId(taskId) },
            predicate = { task -> task?.attempt == 1 }
        )

        val actual = TestRuntime.oppdrag.expectedStatusRequestBody.await()
        assertEquals(oppdragId, actual)

        assertEquals(null, task?.message)
        assertEquals(1, task?.attempt)
        assertEquals(Status.IN_PROGRESS, task?.status)
        val resultat = IverksettingResultater.hent(iverksetting)
        assertNull(resultat.oppdragResultat?.oppdragStatus)
    }

    @Test
    fun `setter task til FAIL når status er OK_UTEN_UTBETALING`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        val oppdragId = TestData.dto.oppdragId(iverksetting)
        val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.OK_UTEN_UTBETALING)
        IverksettingResultater.opprett(iverksetting, null)
        TestRuntime.oppdrag.respondWith(oppdragStatus, oppdragId)
        val taskId = Tasks.create(Kind.SjekkStatus, oppdragId)

        val task = repeatUntil(
            function = { Tasks.forId(taskId) },
            predicate = { task -> task?.attempt == 1 }
        )

        val actual = TestRuntime.oppdrag.expectedStatusRequestBody.await()
        assertEquals(oppdragId, actual)

        assertEquals("Status ${OppdragStatus.OK_UTEN_UTBETALING} skal aldri mottas fra utsjekk-oppdrag.", task?.message)
        assertEquals(1, task?.attempt)
        assertEquals(Status.FAIL, task?.status)
        val resultat = IverksettingResultater.hent(iverksetting)
        assertNull(resultat.oppdragResultat?.oppdragStatus)
    }
}
