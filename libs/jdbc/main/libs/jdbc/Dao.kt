package libs.jdbc

import kotlinx.coroutines.currentCoroutineContext
import libs.jdbc.concurrency.connection
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import libs.utils.daoLog
import libs.utils.secureLog

interface Dao<T: Any> {

    val table: String

    fun from(rs: ResultSet): T

    private suspend fun statement(sql: String): PreparedStatement = 
        currentCoroutineContext().connection.prepareStatement(sql)

    suspend fun query(sql: String, block: (PreparedStatement) -> Unit): List<T> {
        return statement(sql).use { stmt ->
            block(stmt)
            secureLog.debug(stmt.toString())
            stmt.executeQuery().map(::from).also {
                daoLog.debug("$sql -> ${it.size} rows found.")
            }
        }
    }

    suspend fun update(sql: String, block: (PreparedStatement) -> Unit): Int {
        return statement(sql).use { stmt -> 
            block(stmt)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate().also {
                daoLog.debug("$sql -> $it rows affected.")
            }
        }
    }
}


