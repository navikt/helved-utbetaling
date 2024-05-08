package libs.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import libs.postgres.Postgres
import libs.postgres.Postgres.migrate
import libs.postgres.PostgresConfig
import libs.postgres.concurrency.CoroutineDatasource
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction
import libs.utils.appLog
import org.junit.jupiter.api.AfterEach
import javax.sql.DataSource
import kotlin.coroutines.CoroutineContext

abstract class H2 {
    private val datasource: DataSource = Postgres.initialize(config).apply { migrate() }
    val h2: CoroutineContext = CoroutineDatasource(datasource)
    val scope = CoroutineScope(h2)

    private val config
        get() = PostgresConfig(
            host = "stub",
            port = "5432",
            database = "test_db",
            username = "sa",
            password = "",
            url = "jdbc:h2:mem:test_db;MODE=PostgreSQL",
            driver = "org.h2.Driver",
        )

    @AfterEach
    fun clear() = runBlocking {
        scope.async {
            transaction {
                coroutineContext.connection.prepareStatement("SET REFERENTIAL_INTEGRITY FALSE").execute()
                coroutineContext.connection.prepareStatement("TRUNCATE TABLE task").execute()
                coroutineContext.connection.prepareStatement("TRUNCATE TABLE task_log").execute()
                coroutineContext.connection.prepareStatement("SET REFERENTIAL_INTEGRITY TRUE").execute()
            }
        }.await()
        appLog.info("table 'task' trunctated.")
    }
}
