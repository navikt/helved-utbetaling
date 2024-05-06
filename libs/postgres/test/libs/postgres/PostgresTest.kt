package libs.postgres

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.Connection
import java.util.*
import kotlin.test.assertEquals

class TransactionTest : H2() {
    @Test
    fun transaction() {
        datasource.transaction {
            Dao(UUID.randomUUID(), "one").insert(it)
        }
    }

    @Test
    fun rollback() {
        datasource.transaction {
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
