package utsjekk.task

import libs.postgres.concurrency.connection
import libs.postgres.map
import libs.utils.appLog
import libs.utils.secureLog
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.coroutineContext

data class TaskDao(
    val id: UUID = UUID.randomUUID(),
    val kind: Kind,
    val payload: String,
    val status: Status,
    val attempt: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val scheduledFor: LocalDateTime,
    val message: String?,
) {
    suspend fun insert() {
        val sql = """
            INSERT INTO task (id, kind, payload, status, attempt, created_at, updated_at, scheduled_for, message) 
            VALUES (?,?,?,?,?,?,?,?,?)
        """.trimIndent()
        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, id)
            stmt.setString(2, kind.name)
            stmt.setString(3, payload)
            stmt.setString(4, status.name)
            stmt.setObject(5, attempt)
            stmt.setTimestamp(6, Timestamp.valueOf(createdAt))
            stmt.setTimestamp(7, Timestamp.valueOf(updatedAt))
            stmt.setTimestamp(8, Timestamp.valueOf(scheduledFor))
            stmt.setString(9, message)

            appLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }

    suspend fun update() {
        val sql = """
            UPDATE task 
            SET kind = ?, payload = ?, status = ?, attempt = ?, created_at = ?, updated_at = ?, scheduled_for = ?, message = ?
            WHERE id = ?
        """.trimIndent()
        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, kind.name)
            stmt.setString(2, payload)
            stmt.setString(3, status.name)
            stmt.setObject(4, attempt)
            stmt.setTimestamp(5, Timestamp.valueOf(createdAt))
            stmt.setTimestamp(6, Timestamp.valueOf(updatedAt))
            stmt.setTimestamp(7, Timestamp.valueOf(scheduledFor))
            stmt.setString(8, message)
            stmt.setObject(9, id)

            appLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }

    companion object {
        suspend fun select(
            id: UUID? = null,
            kind: Kind? = null,
            payload: String? = null,
            status: List<Status>? = null,
            attempt: Int? = null,
            createdAt: SelectTime? = null,
            updatedAt: SelectTime? = null,
            scheduledFor: SelectTime? = null,
            message: String? = null,
        ): List<TaskDao> {

            val sql = buildString {
                append("SELECT * FROM task WHERE ")

                id?.let { append("id = ? AND ") }
                kind?.let { append("kind = ? AND ") }
                payload?.let { append("payload = ? AND ") }
                status?.let { statuses ->
                    val statuses = statuses.joinToString(", ") { "'$it'" }
                    append("status IN ($statuses) AND ")
                }
                attempt?.let { append("attempt = ? AND ") }
                createdAt?.let { append("created_at ${it.operator.opcode} ? AND ") }
                updatedAt?.let { append("updated_at ${it.operator.opcode} ? AND ") }
                scheduledFor?.let { append("scheduled_for ${it.operator.opcode} ? AND ") }
                message?.let { append("message = ? AND ") }

            }.removeSuffix("AND ")

            // The posistion of the question marks in the sql must be relative to the position in the statement
            var position = 1

            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                id?.let { stmt.setObject(position++, it) }
                kind?.let { stmt.setString(position++, it.name) }
                payload?.let { stmt.setString(position++, it) }
                attempt?.let { stmt.setObject(position++, it) }
                createdAt?.let { stmt.setTimestamp(position++, Timestamp.valueOf(it.time)) }
                updatedAt?.let { stmt.setTimestamp(position++, Timestamp.valueOf(it.time)) }
                scheduledFor?.let { stmt.setTimestamp(position++, Timestamp.valueOf(it.time)) }
                message?.let { stmt.setString(position++, it) }

                appLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from)
            }
        }
    }
}

fun TaskDao.Companion.from(rs: ResultSet) = TaskDao(
    id = UUID.fromString(rs.getString("id")),
    kind = Kind.valueOf(rs.getString("kind")),
    payload = rs.getString("payload"),
    status = Status.valueOf(rs.getString("status")),
    attempt = rs.getInt("attempt"),
    createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
    updatedAt = rs.getTimestamp("updated_at").toLocalDateTime(),
    scheduledFor = rs.getTimestamp("scheduled_for").toLocalDateTime(),
    message = rs.getString("message")
)

/**
 * [column] [operator] [time]
 * eg: created_at >= now()
 */
data class SelectTime(
    val operator: Operator = Operator.LE,
    val time: LocalDateTime = LocalDateTime.now(),
)

enum class Operator(internal val opcode: String) {
    EQ("="), NEQ("!="), IN("IN"), GE(">="), LE("<="),
}
