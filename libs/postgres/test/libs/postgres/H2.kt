package libs.postgres

import libs.postgres.Postgres.migrate
import libs.postgres.concurrency.connection
import org.junit.jupiter.api.AfterEach
import java.util.*
import javax.sql.DataSource
import kotlin.coroutines.coroutineContext

abstract class H2 {
    private val config = PostgresConfig(
        host = "stub",
        port = "5432",
        database = "test_db",
        username = "sa",
        password = "",
        url = "jdbc:h2:mem:test_db;MODE=PostgreSQL",
        driver = "org.h2.Driver",
    )

    val datasource: DataSource = Postgres.initialize(config).apply { migrate() }


    @AfterEach
    fun clear() {
        datasource.transaction { con ->
            con.prepareStatement("TRUNCATE TABLE test").execute()
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