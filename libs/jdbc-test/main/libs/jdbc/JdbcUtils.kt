package libs.jdbc

import kotlinx.coroutines.*
import libs.postgres.Jdbc
import libs.postgres.concurrency.connection
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import libs.postgres.concurrency.transaction
import libs.utils.logger
import javax.sql.DataSource

private val testLog = logger("test")

fun DataSource.truncate(vararg tables: String) = runBlocking {
    withContext(Jdbc.context) {
        transaction {
            tables.forEach {
                coroutineContext.connection.prepareStatement("TRUNCATE TABLE $it CASCADE").execute()
                testLog.info("table '$it' truncated.")
            }
        }
    }
}

fun <T> DataSource.await(timeoutMs: Long = 3_000, query: suspend () -> T?): T? =
    runBlocking {
        withTimeoutOrNull(timeoutMs) {
            channelFlow {
                withContext(Jdbc.context + Dispatchers.IO) {
                    while (true) transaction {
                        query()?.let { send(it) }
                        delay(50)
                    }
                }
            }.firstOrNull()
        }
    }

