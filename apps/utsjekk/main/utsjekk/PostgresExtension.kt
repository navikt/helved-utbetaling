package utsjekk

import libs.utils.appLog
import libs.utils.secureLog
import java.sql.Connection
import javax.sql.DataSource

suspend fun <T> DataSource.ayncTransaction(block: suspend (Connection) -> T): T {
    return this.connection.use { connection ->
        runCatching {
            connection.autoCommit = false
            block(connection)
        }.onSuccess {
            connection.commit()
            connection.autoCommit = true
        }.onFailure {
            appLog.error("Rolling back database transaction, stacktrace in secureLogs")
            secureLog.error("Rolling back database transaction", it)
            connection.rollback()
            connection.autoCommit = true
        }.getOrThrow()
    }
}