package utsjekk.utbetaling

import TestRuntime
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UtbetalingDaoTest {

    @Test
    fun `can insert get and delete utbetaling`() = runTest(TestRuntime.context) {
        val uid = UtbetalingId.random()

        transaction {
            val utbet =
                Utbetaling.dagpenger(1.mar, listOf(Utbetalingsperiode.dagpenger(4.feb, 4.feb, 500u, Satstype.ENGANGS)))
            UtbetalingDao(utbet).insert(uid)
        }
        transaction {
            assertNotNull(UtbetalingDao.findOrNull(uid))
        }
        transaction {
            UtbetalingDao.delete(uid)
        }
        transaction {
            assertNull(UtbetalingDao.findOrNull(uid))
        }
    }

    @Test
    fun `can update utbetaling`() = runTest(TestRuntime.context) {
        val uid = UtbetalingId.random()

        transaction {
            val utbet =
                Utbetaling.dagpenger(1.mar, listOf(Utbetalingsperiode.dagpenger(4.feb, 4.feb, 500u, Satstype.ENGANGS)))
            UtbetalingDao(utbet).insert(uid)
        }
        transaction {
            val utbet = requireNotNull(UtbetalingDao.findOrNull(uid))
            assertEquals(utbet.created_at, utbet.updated_at)
            utbet.update(uid)
        }
        transaction {
            val utbet = requireNotNull(UtbetalingDao.findOrNull(uid))
            assertNotEquals(utbet.created_at, utbet.updated_at)
        }
        transaction {
            UtbetalingDao.delete(uid)
        }
        transaction {
            assertNull(UtbetalingDao.findOrNull(uid))
        }
    }
}

