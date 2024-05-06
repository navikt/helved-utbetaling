package libs.postgres

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import libs.postgres.Postgres.migrate
import libs.postgres.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import javax.sql.DataSource
import kotlin.coroutines.coroutineContext

class ConcurrencyTest {
    private val h2: DataSource = Postgres.initialize(config).apply { migrate() }
    private val scope = CoroutineScope(Dispatchers.IO + CoroutineDatasource(h2))

    @Nested
    inner class Datasource {
        @Test
        fun `can be in context`() = runBlocking {
            scope.async {
                val actual = coroutineContext.datasource
                assertEquals(h2, actual)
            }.await()
        }

        @Test
        fun `fails without context`() {
            val err = assertThrows<IllegalStateException> {
                runBlocking {
                    coroutineContext.datasource
                }
            }
            assertEquals("Datasource not in context", err.message)
        }
    }

    @Nested
    inner class Connection {
        @Test
        fun `can be in context`() = runBlocking {
            scope.async {
                transaction {
                    assertNotNull(coroutineContext.connection)
                }
            }.await()
        }

        @Test
        fun `fails without context`() = runBlocking {
            scope.async {
                val err = assertThrows<IllegalStateException> {
                    coroutineContext.connection
                }
                assertEquals("Connection not in context", err.message)
            }.await()
        }
    }

    @Nested
    inner class Transaction {
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
}

internal data class AsyncDao(
    val id: UUID,
    val data: String,
) {
    suspend fun insert() {
        coroutineContext.connection
            .prepareStatement("INSERT INTO test (id, data) values (?, ?)").use {
                it.setObject(1, id)
                it.setObject(2, data)
                it.executeUpdate()
            }
    }

    suspend fun insertAndThrow() {
        insert()
        error("wops")
    }

    companion object {
        suspend fun count(): Int = coroutineContext.connection
            .prepareStatement("SELECT count(*) FROM test").use { stmt ->
                stmt.executeQuery()
                    .map { it.getInt(1) }
                    .singleOrNull() ?: 0
            }
    }
}

private val config = PostgresConfig(
    host = "stub",
    port = "5432",
    database = "test_db",
    username = "sa",
    password = "",
    url = "jdbc:h2:mem:test_db;MODE=PostgreSQL",
    driver = "org.h2.Driver",
)
