package libs.postgres.concurrency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import libs.utils.appLog
import libs.utils.secureLog
import java.sql.Connection
import java.sql.SQLException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalContracts::class)
suspend inline fun <T> withConnection(crossinline block: suspend CoroutineScope.() -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return if (coroutineContext.hasOpenConnection()) {
        withContext(coroutineContext) {
            block()
        }
    } else {
        val connection = coroutineContext.datasource.connection

        try {
            withContext(CoroutineConnection(connection)) {
                block()
            }
        } finally {
            connection.closeCatching()
        }
    }
}

@PublishedApi
internal fun CoroutineContext.hasOpenConnection(): Boolean {
    val con = get(CoroutineConnection)?.connection
    return con != null && !con.isClosedCatching()
}

@PublishedApi
internal fun Connection.closeCatching() {
    try {
        close()
    } catch (e: SQLException) {
        appLog.warn("Failed to close database connection")
        secureLog.warn("Failed to close database connection", e)
    }
}

@PublishedApi
internal fun Connection.isClosedCatching(): Boolean {
    return try {
        isClosed
    } catch (e: SQLException) {
        appLog.warn("Connection isClosedCatching check failed, already closed?")
        secureLog.warn("Connection isClosedCatching check failed, already closed?", e)
        true
    }
}
