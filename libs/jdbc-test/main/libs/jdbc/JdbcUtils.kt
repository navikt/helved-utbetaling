package libs.jdbc

import kotlinx.coroutines.*
import libs.jdbc.concurrency.connection
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import libs.jdbc.concurrency.transaction
import libs.utils.logger
import javax.sql.DataSource

private val testLog = logger("test")

fun DataSource.truncate(app: String, vararg tables: String) = runBlocking {
    withContext(Jdbc.context) {
        transaction {
            tables.forEach {
                coroutineContext.connection.prepareStatement("TRUNCATE TABLE $it CASCADE").execute()
                testLog.info("table '$app.$it' truncated.")
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

fun <T> DataSource.awaitNull(timeoutMs: Long = 3_000, query: suspend () -> T?): T? =
    runBlocking {
        withTimeoutOrNull(timeoutMs) {
            channelFlow {
                withContext(Jdbc.context + Dispatchers.IO) {
                    while (true) transaction {
                        val res = query()
                        if (res != null) send(res)
                        delay(50)
                    }
                }
            }.firstOrNull()
        }
    }
