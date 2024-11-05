package utsjekk.task.strategies

import TestData
import TestRuntime
import awaitDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import libs.postgres.concurrency.transaction
import libs.task.TaskDao
import libs.task.Tasks
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.iverksett.IverksettStatus
import no.nav.utsjekk.kontrakter.iverksett.StatusEndretMelding
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.IverksettingDao
import utsjekk.iverksetting.behandlingId
import utsjekk.iverksetting.iverksettingId
import utsjekk.iverksetting.resultat.IverksettingResultater
import utsjekk.iverksetting.sakId
import utsjekk.task.Status
import java.time.LocalDateTime
import java.util.UUID

class SjekkStatusStrategyTest {

    private val createdTaskIds = mutableListOf<UUID>()

    @AfterEach
    fun reset() {
        runBlocking {
            withContext(TestRuntime.context) {
                transaction {
                    createdTaskIds
                        .mapNotNull { Tasks.forId(it) }
                        .forEach { it.copy(status = libs.task.Status.COMPLETE).update() }
                }
            }
        }
    }

    @Test
    fun `setter task til COMPLETE når status er KVITTERT_OK`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        val oppdragId = TestData.dto.oppdragId(iverksetting)
        val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_OK)
        IverksettingResultater.opprett(iverksetting, null)
        transaction {
            IverksettingDao(iverksetting, LocalDateTime.now()).insert()
        }

        TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
        TestRuntime.kafka.expect(iverksetting.søker.personident)

        val taskId = Tasks.create(libs.task.Kind.SjekkStatus, oppdragId, null, objectMapper::writeValueAsString)
        createdTaskIds.add(taskId)

        val task = awaitDatabase {
            TaskDao.select {
                it.id = taskId
                it.attempt = 1
            }.firstOrNull()
        }

        TestRuntime.oppdrag.awaitStatus(oppdragId)

        assertEquals("", task?.message)
        assertEquals(1, task?.attempt)
        assertEquals(Status.COMPLETE.name, task?.status?.name)
        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(OppdragStatus.KVITTERT_OK, resultat.oppdragResultat?.oppdragStatus)

        val expectedRecord = StatusEndretMelding(
            sakId = iverksetting.sakId.id,
            behandlingId = iverksetting.behandlingId.id,
            iverksettingId = iverksetting.iverksettingId?.id,
            fagsystem = iverksetting.fagsak.fagsystem,
            status = IverksettStatus.OK,
        )

        assertEquals(expectedRecord, TestRuntime.kafka.waitFor(iverksetting.søker.personident))
    }

    @Test
    fun `setter task til MANUAL når status er KVITTERT_MED_MANGLER`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        val oppdragId = TestData.dto.oppdragId(iverksetting)
        val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_MED_MANGLER, "mangelvare")
        IverksettingResultater.opprett(iverksetting, null)
        transaction {
            IverksettingDao(iverksetting, LocalDateTime.now()).insert()
        }

        TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
        TestRuntime.kafka.expect(iverksetting.søker.personident)

        val taskId = Tasks.create(libs.task.Kind.SjekkStatus, oppdragId, null, objectMapper::writeValueAsString)
        createdTaskIds.add(taskId)

        val task = awaitDatabase {
            TaskDao.select {
                it.id = taskId
                it.attempt = 1
            }.firstOrNull()
        }

        TestRuntime.oppdrag.awaitStatus(oppdragId)

        assertEquals("mangelvare", task?.message)
        assertEquals(1, task?.attempt)
        assertEquals(Status.MANUAL.name, task?.status?.name)
        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(OppdragStatus.KVITTERT_MED_MANGLER, resultat.oppdragResultat?.oppdragStatus)

        val expectedRecord = StatusEndretMelding(
            sakId = iverksetting.sakId.id,
            behandlingId = iverksetting.behandlingId.id,
            iverksettingId = iverksetting.iverksettingId?.id,
            fagsystem = iverksetting.fagsak.fagsystem,
            status = IverksettStatus.FEILET_MOT_OPPDRAG,
        )

        assertEquals(expectedRecord, TestRuntime.kafka.waitFor(iverksetting.søker.personident))
    }

    @Test
    fun `setter task til MANUAL når status er KVITTERT_TEKNISK_FEIL`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        val oppdragId = TestData.dto.oppdragId(iverksetting)
        val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_TEKNISK_FEIL, "teknisk")
        IverksettingResultater.opprett(iverksetting, null)
        transaction {
            IverksettingDao(iverksetting, LocalDateTime.now()).insert()
        }

        TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
        TestRuntime.kafka.expect(iverksetting.søker.personident)

        val taskId = Tasks.create(libs.task.Kind.SjekkStatus, oppdragId, null, objectMapper::writeValueAsString)
        createdTaskIds.add(taskId)

        val task = awaitDatabase {
            TaskDao.select {
                it.id = taskId
                it.attempt = 1
            }.firstOrNull()
        }

        TestRuntime.oppdrag.awaitStatus(oppdragId)

        assertEquals("teknisk", task?.message)
        assertEquals(1, task?.attempt)
        assertEquals(Status.MANUAL.name, task?.status?.name)
        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(OppdragStatus.KVITTERT_TEKNISK_FEIL, resultat.oppdragResultat?.oppdragStatus)

        val expectedRecord = StatusEndretMelding(
            sakId = iverksetting.sakId.id,
            behandlingId = iverksetting.behandlingId.id,
            iverksettingId = iverksetting.iverksettingId?.id,
            fagsystem = iverksetting.fagsak.fagsystem,
            status = IverksettStatus.FEILET_MOT_OPPDRAG,
        )

        assertEquals(expectedRecord, TestRuntime.kafka.waitFor(iverksetting.søker.personident))
    }

    @Test
    fun `setter task til MANUAL når status er KVITTERT_FUNKSJONELL_FEIL`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        val oppdragId = TestData.dto.oppdragId(iverksetting)
        val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_FUNKSJONELL_FEIL, "funkis")
        IverksettingResultater.opprett(iverksetting, null)
        transaction {
            IverksettingDao(iverksetting, LocalDateTime.now()).insert()
        }

        TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
        TestRuntime.kafka.expect(iverksetting.søker.personident)

        val taskId = Tasks.create(libs.task.Kind.SjekkStatus, oppdragId, null, objectMapper::writeValueAsString)
        createdTaskIds.add(taskId)

        val task = awaitDatabase {
            TaskDao.select {
                it.id = taskId
                it.attempt = 1
            }.firstOrNull()
        }

        TestRuntime.oppdrag.awaitStatus(oppdragId)

        assertEquals("funkis", task?.message)
        assertEquals(1, task?.attempt)
        assertEquals(Status.MANUAL.name, task?.status?.name)
        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(OppdragStatus.KVITTERT_FUNKSJONELL_FEIL, resultat.oppdragResultat?.oppdragStatus)

        val expectedRecord = StatusEndretMelding(
            sakId = iverksetting.sakId.id,
            behandlingId = iverksetting.behandlingId.id,
            iverksettingId = iverksetting.iverksettingId?.id,
            fagsystem = iverksetting.fagsak.fagsystem,
            status = IverksettStatus.FEILET_MOT_OPPDRAG,
        )

        assertEquals(expectedRecord, TestRuntime.kafka.waitFor(iverksetting.søker.personident))
    }

    @Test
    fun `setter task til MANUAL når status er KVITTERT_UKJENT`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        val oppdragId = TestData.dto.oppdragId(iverksetting)
        val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_UKJENT)
        IverksettingResultater.opprett(iverksetting, null)
        TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
        transaction {
            IverksettingDao(iverksetting, LocalDateTime.now()).insert()
        }
        val taskId = Tasks.create(libs.task.Kind.SjekkStatus, oppdragId, null, objectMapper::writeValueAsString)
        createdTaskIds.add(taskId)

        val task = awaitDatabase {
            TaskDao.select {
                it.id = taskId
                it.attempt = 1
            }.firstOrNull()
        }

        TestRuntime.oppdrag.awaitStatus(oppdragId)

        assertEquals("Ukjent kvittering fra OS", task?.message)
        assertEquals(1, task?.attempt)
        assertEquals(Status.MANUAL.name, task?.status?.name)
        val resultat = IverksettingResultater.hent(iverksetting)
        assertEquals(OppdragStatus.KVITTERT_UKJENT, resultat.oppdragResultat?.oppdragStatus)
    }

    @Test
    fun `oppdaterer antall forsøk når status er LAGT_PÅ_KØ`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        val oppdragId = TestData.dto.oppdragId(iverksetting)
        val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.LAGT_PÅ_KØ)
        IverksettingResultater.opprett(iverksetting, null)
        transaction {
            IverksettingDao(iverksetting, LocalDateTime.now()).insert()
        }
        TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
        val taskId = Tasks.create(libs.task.Kind.SjekkStatus, oppdragId, null, objectMapper::writeValueAsString)
        createdTaskIds.add(taskId)

        val task = awaitDatabase {
            TaskDao.select {
                it.id = taskId
                it.attempt = 1
            }.firstOrNull()
        }

        TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)

        assertEquals(null, task?.message)
        assertEquals(1, task?.attempt)
        assertEquals(Status.IN_PROGRESS.name, task?.status?.name)
        val resultat = IverksettingResultater.hent(iverksetting)
        assertNull(resultat.oppdragResultat?.oppdragStatus)
    }

    @Test
    fun `setter task til FAIL når status er OK_UTEN_UTBETALING`() = runTest(TestRuntime.context) {
        val iverksetting = TestData.domain.iverksetting()
        val oppdragId = TestData.dto.oppdragId(iverksetting)
        val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.OK_UTEN_UTBETALING)
        IverksettingResultater.opprett(iverksetting, null)
        transaction {
            IverksettingDao(iverksetting, LocalDateTime.now()).insert()
        }
        TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
        val taskId = Tasks.create(libs.task.Kind.SjekkStatus, oppdragId, null, objectMapper::writeValueAsString)
        createdTaskIds.add(taskId)

        val task = awaitDatabase {
            TaskDao.select {
                it.id = taskId
                it.attempt = 1
            }.firstOrNull()
        }

        TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)

        assertEquals(
            "Status ${OppdragStatus.OK_UTEN_UTBETALING} skal aldri mottas fra utsjekk-oppdrag.",
            task?.message,
        )
        assertEquals(1, task?.attempt)
        assertEquals(Status.FAIL.name, task?.status?.name)
        val resultat = IverksettingResultater.hent(iverksetting)
        assertNull(resultat.oppdragResultat?.oppdragStatus)
    }
}
