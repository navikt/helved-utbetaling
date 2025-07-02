package libs.jdbc

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.connection
import libs.jdbc.concurrency.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrationTest {
    private val ctx = CoroutineDatasource(Jdbc.initialize(h2))

    @AfterEach
    fun cleanup() {
        runBlocking {
            withContext(ctx) {
                transaction {
                    coroutineContext.connection.prepareStatement("DROP TABLE IF EXISTS migrations, test_table, test_table2, test_table3")
                        .execute()
                }
            }
        }
    }

    @Test
    fun `location is not a dir`() = runTest {
        val err = assertThrows<IllegalStateException> {
            Migrator(File("test/migrations/valid/1.sql"))
        }

        val expected = "Specified location is not a directory: ${File("test/migrations/valid/1.sql").absolutePath}"
        val actual = requireNotNull(err.message)

        assertTrue(
            actual.contains(expected),
            """
               excpected: $expected
               actual:    $actual
            """.trimMargin()
        )
    }

    @Test
    fun `allow no files`() = runTest(ctx) {
        Migrator(File("test/migrations/empty"))
    }

    @Test
    fun `can create migrations table`() = runTest {
        Migrator(File("test/migrations/valid"))
        Migrator(File("test/migrations/valid"))
    }

    @Test
    fun `can migrate scripts`() = runTest(ctx) {
        Migrator(File("test/migrations/valid")).migrate()
    }

    @Test
    fun `can migrate scripts from multiple locations`() = runTest(ctx) {
        Migrator(
            listOf(
                File("test/migrations/valid"),
                File("test/migrations2/"),
            )
        ).migrate()
    }

    @Test
    fun `migrations are idempotent`() = runTest(ctx) {
        val migrator = Migrator(File("test/migrations/valid"))
        migrator.migrate()
        migrator.migrate()
    }

    @Test
    fun `sequence is corrupted`() = runTest(ctx) {
        val migrator = Migrator(File("test/migrations/wrong_seq"))
        val err = assertThrows<IllegalStateException> {
            migrator.migrate()
        }
        assertEquals("A version was not incremented by 1: registred order: 1, 3", err.message)
    }

    @Test
    fun `checksum is corrupted`() = runTest(ctx) {
        Migrator(File("test/migrations/valid")).migrate()

        val err = assertThrows<IllegalStateException> {
            Migrator(File("test/migrations/wrong_checksum")).migrate()
        }
        assertEquals("Checksum differs from existing migration: 1.sql", err.message)
    }

    @Test
    fun `can read utf8 filenames`() = runTest(ctx) {
        Migrator(File("test/migrations/utf8")).migrate()
    }
}

private val h2 = JdbcConfig(
    host = "stub",
    port = "5432",
    database = "test_db",
    username = "sa",
    password = "",
    url = "jdbc:h2:mem:test_db;MODE=PostgreSQL",
    driver = "org.h2.Driver",
)
