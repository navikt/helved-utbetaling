package libs.postgres

import libs.postgres.Postgres.migrate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.Connection
import java.util.*
import javax.sql.DataSource
import kotlin.test.assertEquals

class TransactionTest {
    private val h2: DataSource = Postgres.initialize(config).apply { migrate() }

    @Test
    fun transaction() {
        h2.transaction {
            Dao(UUID.randomUUID(), "one").insert(it)
        }
    }

    @Test
    fun rollback() {
        h2.transaction {
            val err = assertThrows<IllegalStateException> {
                Dao(UUID.randomUUID(), "two").insertAndThrow(it)
            }
            assertEquals(err.message, "wops")
        }
    }
}

internal data class Dao(
    val id: UUID,
    val data: String,
) {
    fun insert(con: Connection) {
        con.prepareStatement("INSERT INTO test (id, data) values (?, ?)").use {
            it.setObject(1, id)
            it.setObject(2, data)
            it.executeUpdate()
        }
    }

    fun insertAndThrow(con: Connection) {
        insert(con)
        error("wops")
    }

    companion object {
        fun count(con: Connection): Int =
            con.prepareStatement("SELECT count(*) FROM test").use { stmt ->
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

