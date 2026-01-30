package libs.jdbc

import kotlinx.coroutines.currentCoroutineContext
import libs.jdbc.concurrency.connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import libs.utils.jdbcLog
import libs.utils.secureLog

interface Dao<T: Any> {

    val table: String

    fun from(rs: ResultSet): T

    suspend fun statement(sql: String): PreparedStatement = 
        currentCoroutineContext().connection.prepareStatement(sql)

    suspend fun query(sql: String, block: (PreparedStatement) -> Unit = {}): List<T> {
        return statement(sql).use { stmt ->
            block(stmt)
            secureLog.debug(stmt.toString())
            stmt.executeQuery().map(::from).also {
                jdbcLog.debug("$sql :: ${it.size} rows found.")
            }
        }
    }

    suspend fun <U> query(sql: String, mapper: (ResultSet) -> U, block: (PreparedStatement) -> Unit): List<U?> {
        return statement(sql).use { stmt ->
            block(stmt)
            secureLog.debug(stmt.toString())
            stmt.executeQuery().map(mapper).also {
                jdbcLog.debug("$sql :: ${it.size} rows found.")
            }
        }
    }

    suspend fun update(sql: String, block: (PreparedStatement) -> Unit): Int {
        return statement(sql).use { stmt -> 
            block(stmt)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate().also {
                jdbcLog.debug("$sql :: $it rows affected.")
            }
        }
    }
}


