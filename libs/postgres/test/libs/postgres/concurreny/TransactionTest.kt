package libs.postgres.concurreny

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import libs.postgres.AsyncDao
import libs.postgres.H2
import libs.postgres.concurrency.CoroutineDatasource
import libs.postgres.concurrency.CoroutineTransaction
import libs.postgres.concurrency.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

class TransactionTest : H2() {
    private val scope = CoroutineScope(Dispatchers.IO + CoroutineDatasource(datasource))

    @Test
    fun `can be in context`() = runBlocking {
        scope.async {
            transaction {
                assertEquals(0, AsyncDao.count())
            }
        }.await()
    }

    @Test
    fun `fails without context`() = runBlocking {
        scope.async {
            val err = assertThrows<IllegalStateException> {
                runBlocking {
                    AsyncDao.count()
                }
            }
            assertEquals(err.message, "Connection not in context")

        }.await()
    }

    @Test
    fun `can be nested with rollbacks`() = runBlocking {
        scope.async {
            transaction {
                assertEquals(0, AsyncDao.count())
            }

            runCatching {
                transaction {
                    transaction {
                        AsyncDao(UUID.randomUUID(), "one").insert()
                    }
                    transaction {
                        AsyncDao(UUID.randomUUID(), "two").insertAndThrow()
                    }
                }
            }

            transaction {
                assertEquals(0, AsyncDao.count())
            }
        }.await()
    }

    @Test
    fun `can be nested with commits`() = runBlocking {
        scope.async {
            runCatching {
                transaction {
                    assertEquals(0, AsyncDao.count())
                }

                transaction {
                    transaction {
                        AsyncDao(UUID.randomUUID(), "one").insert()
                    }
                    transaction {
                        AsyncDao(UUID.randomUUID(), "two").insert()
                    }
                }
            }

            transaction {
                assertEquals(2, AsyncDao.count())
            }
        }.await()
    }

    @Test
    fun `can be completed`() {
        val transaction = CoroutineTransaction()
        assertFalse(transaction.completed)
        transaction.complete()
        assertTrue(transaction.completed)
    }

}