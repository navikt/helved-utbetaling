package utsjekk.task.strategies

import TestData
import TestRuntime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import libs.postgres.concurrency.transaction
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.IverksettingDao
import utsjekk.iverksetting.resultat.IverksettingResultatDao
import utsjekk.iverksetting.resultat.IverksettingResultater
import utsjekk.task.*

class SjekkStatusStrategyTest {
    @BeforeEach
    fun reset() {

    }

//    @Test
//    fun `setter task til COMPLETE når status er KVITTERT_OK`() =
//        runTest(TestRuntime.context) {
//            withContext(TestRuntime.context) {
//                val iverksetting = TestData.domain.iverksetting()
//                val oppdragId = TestData.dto.oppdragId(iverksetting)
//                val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_OK)
//                IverksettingResultater.opprett(iverksetting, null)
//                TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
//                val taskId = Tasks.create(Kind.SjekkStatus, oppdragId)
//                val task =
//                    runBlocking {
//                        suspend fun getTask(attempt: Int): TaskDto? =
//                            withContext(TestRuntime.context) {
//                                val actual = transaction { Tasks.forId(taskId) }
//                                if (actual?.attempt != 1 && attempt < 1000) {
//                                    getTask(attempt + 1)
//                                } else {
//                                    actual
//                                }
//                            }
//                        getTask(0)
//                    }
//
//                TestRuntime.oppdrag.awaitStatus(oppdragId)
//
//                assertEquals("", task?.message)
//                assertEquals(1, task?.attempt)
//                assertEquals(Status.COMPLETE, task?.status)
//                val resultat = IverksettingResultater.hent(iverksetting)
//                assertEquals(OppdragStatus.KVITTERT_OK, resultat.oppdragResultat?.oppdragStatus)
//            }
//        }
//
//    @Test
//    fun `setter task til MANUAL når status er KVITTERT_MED_MANGLER`() =
//        runTest {
//            withContext(TestRuntime.context) {
//                val iverksetting = TestData.domain.iverksetting()
//                val oppdragId = TestData.dto.oppdragId(iverksetting)
//                val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_MED_MANGLER, "mangelvare")
//                IverksettingResultater.opprett(iverksetting, null)
//                TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
//                val taskId = Tasks.create(Kind.SjekkStatus, oppdragId)
//                val task =
//                    runBlocking {
//                        suspend fun getTask(attempt: Int): TaskDto? =
//                            withContext(TestRuntime.context) {
//                                val actual = transaction { Tasks.forId(taskId) }
//                                if (actual?.attempt != 1 && attempt < 800) {
//                                    getTask(attempt + 1)
//                                } else {
//                                    actual
//                                }
//                            }
//                        getTask(0)
//                    }
//
//                TestRuntime.oppdrag.awaitStatus(oppdragId)
//
//                assertEquals("mangelvare", task?.message)
//                assertEquals(1, task?.attempt)
//                assertEquals(Status.MANUAL, task?.status)
//                val resultat = IverksettingResultater.hent(iverksetting)
//                assertEquals(OppdragStatus.KVITTERT_MED_MANGLER, resultat.oppdragResultat?.oppdragStatus)
//            }
//        }
//
//    @Test
//    fun `setter task til MANUAL når status er KVITTERT_TEKNISK_FEIL`() =
//        runTest {
//            withContext(TestRuntime.context) {
//                val iverksetting = TestData.domain.iverksetting()
//                val oppdragId = TestData.dto.oppdragId(iverksetting)
//                val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_TEKNISK_FEIL, "teknisk")
//                IverksettingResultater.opprett(iverksetting, null)
//                TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
//                val taskId = Tasks.create(Kind.SjekkStatus, oppdragId)
//                val task =
//                    runBlocking {
//                        suspend fun getTask(attempt: Int): TaskDto? =
//                            withContext(TestRuntime.context) {
//                                val actual = transaction { Tasks.forId(taskId) }
//                                if (actual?.attempt != 1 && attempt < 800) {
//                                    getTask(attempt + 1)
//                                } else {
//                                    actual
//                                }
//                            }
//                        getTask(0)
//                    }
//
//                TestRuntime.oppdrag.awaitStatus(oppdragId)
//
//                assertEquals("teknisk", task?.message)
//                assertEquals(1, task?.attempt)
//                assertEquals(Status.MANUAL, task?.status)
//                val resultat = IverksettingResultater.hent(iverksetting)
//                assertEquals(OppdragStatus.KVITTERT_TEKNISK_FEIL, resultat.oppdragResultat?.oppdragStatus)
//            }
//        }
//
//    @Test
//    fun `setter task til MANUAL når status er KVITTERT_FUNKSJONELL_FEIL`() =
//        runTest {
//            withContext(TestRuntime.context) {
//                val iverksetting = TestData.domain.iverksetting()
//                val oppdragId = TestData.dto.oppdragId(iverksetting)
//                val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_FUNKSJONELL_FEIL, "funkis")
//                IverksettingResultater.opprett(iverksetting, null)
//                TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
//                val taskId = Tasks.create(Kind.SjekkStatus, oppdragId)
//                val task =
//                    runBlocking {
//                        suspend fun getTask(attempt: Int): TaskDto? =
//                            withContext(TestRuntime.context) {
//                                val actual = transaction { Tasks.forId(taskId) }
//                                if (actual?.attempt != 1 && attempt < 800) {
//                                    getTask(attempt + 1)
//                                } else {
//                                    actual
//                                }
//                            }
//                        getTask(0)
//                    }
//
//                TestRuntime.oppdrag.awaitStatus(oppdragId)
//
//                assertEquals("funkis", task?.message)
//                assertEquals(1, task?.attempt)
//                assertEquals(Status.MANUAL, task?.status)
//                val resultat = IverksettingResultater.hent(iverksetting)
//                assertEquals(OppdragStatus.KVITTERT_FUNKSJONELL_FEIL, resultat.oppdragResultat?.oppdragStatus)
//            }
//        }
//
//    @Test
//    fun `setter task til MANUAL når status er KVITTERT_UKJENT`() =
//        runTest {
//            withContext(TestRuntime.context) {
//                val iverksetting = TestData.domain.iverksetting()
//                val oppdragId = TestData.dto.oppdragId(iverksetting)
//                val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.KVITTERT_UKJENT)
//                IverksettingResultater.opprett(iverksetting, null)
//                TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
//                val taskId = Tasks.create(Kind.SjekkStatus, oppdragId)
//                val task =
//                    runBlocking {
//                        suspend fun getTask(attempt: Int): TaskDto? =
//                            withContext(TestRuntime.context) {
//                                val actual = transaction { Tasks.forId(taskId) }
//                                if (actual?.attempt != 1 && attempt < 800) {
//                                    getTask(attempt + 1)
//                                } else {
//                                    actual
//                                }
//                            }
//                        getTask(0)
//                    }
//
//                TestRuntime.oppdrag.awaitStatus(oppdragId)
//
//                assertEquals("Ukjent kvittering fra OS", task?.message)
//                assertEquals(1, task?.attempt)
//                assertEquals(Status.MANUAL, task?.status)
//                val resultat = IverksettingResultater.hent(iverksetting)
//                assertEquals(OppdragStatus.KVITTERT_UKJENT, resultat.oppdragResultat?.oppdragStatus)
//            }
//        }
//
//    @Test
//    fun `oppdaterer antall forsøk når status er LAGT_PÅ_KØ`() =
//        runTest {
//            withContext(TestRuntime.context) {
//                val iverksetting = TestData.domain.iverksetting()
//                val oppdragId = TestData.dto.oppdragId(iverksetting)
//                val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.LAGT_PÅ_KØ)
//                IverksettingResultater.opprett(iverksetting, null)
//                TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
//                val taskId = Tasks.create(Kind.SjekkStatus, oppdragId)
//                val task =
//                    runBlocking {
//                        suspend fun getTask(attempt: Int): TaskDto? =
//                            withContext(TestRuntime.context) {
//                                val actual = transaction { Tasks.forId(taskId) }
//                                if (actual?.attempt != 1 && attempt < 800) {
//                                    getTask(attempt + 1)
//                                } else {
//                                    actual
//                                }
//                            }
//                        getTask(0)
//                    }
//
//                TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
//
//                assertEquals(null, task?.message)
//                assertEquals(1, task?.attempt)
//                assertEquals(Status.IN_PROGRESS, task?.status)
//                val resultat = IverksettingResultater.hent(iverksetting)
//                assertNull(resultat.oppdragResultat?.oppdragStatus)
//            }
//        }
//
//    @Test
//    fun `setter task til FAIL når status er OK_UTEN_UTBETALING`() =
//        runTest {
//            withContext(TestRuntime.context) {
//                val iverksetting = TestData.domain.iverksetting()
//                val oppdragId = TestData.dto.oppdragId(iverksetting)
//                val oppdragStatus = TestData.dto.oppdragStatus(OppdragStatus.OK_UTEN_UTBETALING)
//                IverksettingResultater.opprett(iverksetting, null)
//                TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
//                val taskId = Tasks.create(Kind.SjekkStatus, oppdragId)
//                val task =
//                    runBlocking {
//                        suspend fun getTask(attempt: Int): TaskDto? =
//                            withContext(TestRuntime.context) {
//                                val actual = transaction { Tasks.forId(taskId) }
//                                if (actual?.attempt != 1 && attempt < 800) {
//                                    getTask(attempt + 1)
//                                } else {
//                                    actual
//                                }
//                            }
//                        getTask(0)
//                    }
//
//                TestRuntime.oppdrag.statusRespondWith(oppdragId, oppdragStatus)
//
//                assertEquals(
//                    "Status ${OppdragStatus.OK_UTEN_UTBETALING} skal aldri mottas fra utsjekk-oppdrag.",
//                    task?.message,
//                )
//                assertEquals(1, task?.attempt)
//                assertEquals(Status.FAIL, task?.status)
//                val resultat = IverksettingResultater.hent(iverksetting)
//                assertNull(resultat.oppdragResultat?.oppdragStatus)
//            }
//        }
}
