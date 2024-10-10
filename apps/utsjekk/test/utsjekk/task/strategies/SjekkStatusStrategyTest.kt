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
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.resultat.IverksettingResultater
import utsjekk.task.Status
import java.util.*

//@org.junit.jupiter.api.Disabled
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
        withContext(TestRuntime.context) {
            val iverksetting = TestData.domain.iverksetting()
            val oppdragId = TestData.dto.oppdragId(iverksetting)
            val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_OK)
            IverksettingResultater.opprett(iverksetting, null)
            TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
            val taskId = Tasks.create(libs.task.Kind.SjekkStatus, oppdragId, null, objectMapper::writeValueAsString)
            createdTaskIds.add(taskId)

            val task = awaitDatabase {
                TaskDao.select {
                    it.id = taskId
                    it.attempt = 1
                }.firstOrNull()
            }
//            val task = runBlocking {
//                suspend fun getTask(attempt: Int): TaskDao? =
//                    withContext(TestRuntime.context) {
//                        val actual = transaction { Tasks.forId(taskId) }
//                        if (actual?.attempt != 1 && attempt < 1000) {
//                            getTask(attempt + 1)
//                        } else {
//                            actual
//                        }
//                    }
//                getTask(0)
//            }

            TestRuntime.oppdrag.awaitStatus(oppdragId)

            assertEquals("", task?.message)
            assertEquals(1, task?.attempt)
            assertEquals(Status.COMPLETE.name, task?.status?.name)
            val resultat = IverksettingResultater.hent(iverksetting)
            assertEquals(OppdragStatus.KVITTERT_OK, resultat.oppdragResultat?.oppdragStatus)
        }
    }

    @Test
    fun `setter task til MANUAL når status er KVITTERT_MED_MANGLER`() =
        runTest {
            withContext(TestRuntime.context) {
                val iverksetting = TestData.domain.iverksetting()
                val oppdragId = TestData.dto.oppdragId(iverksetting)
                val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_MED_MANGLER, "mangelvare")
                IverksettingResultater.opprett(iverksetting, null)
                TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
                val taskId = Tasks.create(libs.task.Kind.SjekkStatus, oppdragId, null, objectMapper::writeValueAsString)
                createdTaskIds.add(taskId)

                val task = awaitDatabase {
                    TaskDao.select {
                        it.id = taskId
                        it.attempt = 1
                    }.firstOrNull()
                }
//                val task = runBlocking {
//                    suspend fun getTask(attempt: Int): TaskDao? =
//                        withContext(TestRuntime.context) {
//                            val actual = transaction { Tasks.forId(taskId) }
//                            if (actual?.attempt != 1 && attempt < 800) {
//                                getTask(attempt + 1)
//                            } else {
//                                actual
//                            }
//                        }
//                    getTask(0)
//                }

                TestRuntime.oppdrag.awaitStatus(oppdragId)

                assertEquals("mangelvare", task?.message)
                assertEquals(1, task?.attempt)
                assertEquals(Status.MANUAL.name, task?.status?.name)
                val resultat = IverksettingResultater.hent(iverksetting)
                assertEquals(OppdragStatus.KVITTERT_MED_MANGLER, resultat.oppdragResultat?.oppdragStatus)
            }
        }

    @Test
    fun `setter task til MANUAL når status er KVITTERT_TEKNISK_FEIL`() = runTest {
        withContext(TestRuntime.context) {
            val iverksetting = TestData.domain.iverksetting()
            val oppdragId = TestData.dto.oppdragId(iverksetting)
            val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_TEKNISK_FEIL, "teknisk")
            IverksettingResultater.opprett(iverksetting, null)
            TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)

            val taskId = Tasks.create(libs.task.Kind.SjekkStatus, oppdragId, null, objectMapper::writeValueAsString)
            createdTaskIds.add(taskId)

            val task = awaitDatabase {
                TaskDao.select {
                    it.id = taskId
                    it.attempt = 1
                }.firstOrNull()
            }
//            val task =
//                runBlocking {
//                    suspend fun getTask(attempt: Int): TaskDao? =
//                        withContext(TestRuntime.context) {
//                            val actual = transaction { Tasks.forId(taskId) }
//                            if (actual?.attempt != 1 && attempt < 800) {
//                                getTask(attempt + 1)
//                            } else {
//                                actual
//                            }
//                        }
//                    getTask(0)
//                }

            TestRuntime.oppdrag.awaitStatus(oppdragId)

            assertEquals("teknisk", task?.message)
            assertEquals(1, task?.attempt)
            assertEquals(Status.MANUAL.name, task?.status?.name)
            val resultat = IverksettingResultater.hent(iverksetting)
            assertEquals(OppdragStatus.KVITTERT_TEKNISK_FEIL, resultat.oppdragResultat?.oppdragStatus)
        }
    }

    @Test
    fun `setter task til MANUAL når status er KVITTERT_FUNKSJONELL_FEIL`() = runTest {
        withContext(TestRuntime.context) {
            val iverksetting = TestData.domain.iverksetting()
            val oppdragId = TestData.dto.oppdragId(iverksetting)
            val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_FUNKSJONELL_FEIL, "funkis")
            IverksettingResultater.opprett(iverksetting, null)
            TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
            val taskId = Tasks.create(libs.task.Kind.SjekkStatus, oppdragId, null, objectMapper::writeValueAsString)

            createdTaskIds.add(taskId)

            val task = awaitDatabase {
                TaskDao.select {
                    it.id = taskId
                    it.attempt = 1
                }.firstOrNull()
            }
//            val task = runBlocking {
//                suspend fun getTask(attempt: Int): TaskDao? =
//                    withContext(TestRuntime.context) {
//                        val actual = transaction { Tasks.forId(taskId) }
//                        if (actual?.attempt != 1 && attempt < 800) {
//                            getTask(attempt + 1)
//                        } else {
//                            actual
//                        }
//                    }
//                getTask(0)
//            }

            TestRuntime.oppdrag.awaitStatus(oppdragId)

            assertEquals("funkis", task?.message)
            assertEquals(1, task?.attempt)
            assertEquals(Status.MANUAL.name, task?.status?.name)
            val resultat = IverksettingResultater.hent(iverksetting)
            assertEquals(OppdragStatus.KVITTERT_FUNKSJONELL_FEIL, resultat.oppdragResultat?.oppdragStatus)
        }
    }

    @Test
    fun `setter task til MANUAL når status er KVITTERT_UKJENT`() = runTest {
        withContext(TestRuntime.context) {
            val iverksetting = TestData.domain.iverksetting()
            val oppdragId = TestData.dto.oppdragId(iverksetting)
            val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_UKJENT)
            IverksettingResultater.opprett(iverksetting, null)
            TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
            val taskId = Tasks.create(libs.task.Kind.SjekkStatus, oppdragId, null, objectMapper::writeValueAsString)
            createdTaskIds.add(taskId)

            val task = awaitDatabase {
                TaskDao.select {
                    it.id = taskId
                    it.attempt = 1
                }.firstOrNull()
            }
//            val task =
//                runBlocking {
//                    suspend fun getTask(attempt: Int): TaskDao? =
//                        withContext(TestRuntime.context) {
//                            val actual = transaction { Tasks.forId(taskId) }
//                            if (actual?.attempt != 1 && attempt < 800) {
//                                getTask(attempt + 1)
//                            } else {
//                                actual
//                            }
//                        }
//                    getTask(0)
//                }

            TestRuntime.oppdrag.awaitStatus(oppdragId)

            assertEquals("Ukjent kvittering fra OS", task?.message)
            assertEquals(1, task?.attempt)
            assertEquals(Status.MANUAL.name, task?.status?.name)
            val resultat = IverksettingResultater.hent(iverksetting)
            assertEquals(OppdragStatus.KVITTERT_UKJENT, resultat.oppdragResultat?.oppdragStatus)
        }
    }

    @Test
    fun `oppdaterer antall forsøk når status er LAGT_PÅ_KØ`() = runTest {
        withContext(TestRuntime.context) {
            val iverksetting = TestData.domain.iverksetting()
            val oppdragId = TestData.dto.oppdragId(iverksetting)
            val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.LAGT_PÅ_KØ)
            IverksettingResultater.opprett(iverksetting, null)
            TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
            val taskId = Tasks.create(libs.task.Kind.SjekkStatus, oppdragId, null, objectMapper::writeValueAsString)
            createdTaskIds.add(taskId)

            val task = awaitDatabase {
                TaskDao.select {
                    it.id = taskId
                    it.attempt = 1
                }.firstOrNull()
            }
//            val task = runBlocking {
//                suspend fun getTask(attempt: Int): TaskDao? =
//                    withContext(TestRuntime.context) {
//                        val actual = transaction { Tasks.forId(taskId) }
//                        if (actual?.attempt != 1 && attempt < 800) {
//                            getTask(attempt + 1)
//                        } else {
//                            actual
//                        }
//                    }
//                getTask(0)
//            }

            TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)

            assertEquals(null, task?.message)
            assertEquals(1, task?.attempt)
            assertEquals(Status.IN_PROGRESS.name, task?.status?.name)
            val resultat = IverksettingResultater.hent(iverksetting)
            assertNull(resultat.oppdragResultat?.oppdragStatus)
        }
    }

    @Test
    fun `setter task til FAIL når status er OK_UTEN_UTBETALING`() = runTest {
        withContext(TestRuntime.context) {
            val iverksetting = TestData.domain.iverksetting()
            val oppdragId = TestData.dto.oppdragId(iverksetting)
            val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.OK_UTEN_UTBETALING)
            IverksettingResultater.opprett(iverksetting, null)
            TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
            val taskId = Tasks.create(libs.task.Kind.SjekkStatus, oppdragId, null, objectMapper::writeValueAsString)
            createdTaskIds.add(taskId)

            val task = awaitDatabase {
                TaskDao.select {
                    it.id = taskId
                    it.attempt = 1
                }.firstOrNull()
            }
//            val task = runBlocking {
//                suspend fun getTask(attempt: Int): TaskDao? =
//                    withContext(TestRuntime.context) {
//                        val actual = transaction { Tasks.forId(taskId) }
//                        if (actual?.attempt != 1 && attempt < 800) {
//                            getTask(attempt + 1)
//                        } else {
//                            actual
//                        }
//                    }
//                getTask(0)
//            }

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
}
