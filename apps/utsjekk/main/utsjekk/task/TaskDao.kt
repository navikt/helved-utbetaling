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
        val sql =
            """
            INSERT INTO $TABLE_NAME (id, kind, payload, status, attempt, created_at, updated_at, scheduled_for, message) 
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
        val sql =
            """
            UPDATE $TABLE_NAME 
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
        const val TABLE_NAME = "task_v2"

        suspend fun select(
            limit: Int? = null,
            offset: Int? = null,
            conditions: (where: Where) -> Unit = {},
        ): List<TaskDao> {
            val conditions = Where().apply(conditions)
            val sql =
                buildString {
                    append("SELECT * FROM $TABLE_NAME")
                    if (conditions.any()) {
                        append(" WHERE ")
                        conditions.id?.let { append("id = ? AND ") }
                        conditions.kind?.let { append("kind = ? AND ") }
                        conditions.payload?.let { append("payload = ? AND ") }
                        conditions.status?.let {
                            val statuses = it.joinToString(", ") { status -> "'$status'" }
                            append("status IN ($statuses) AND ")
                        }
                        conditions.attempt?.let { append("attempt = ? AND ") }
                        conditions.createdAt?.let { append("created_at ${it.operator.opcode} ? AND ") }
                        conditions.updatedAt?.let { append("updated_at ${it.operator.opcode} ? AND ") }
                        conditions.scheduledFor?.let { append("scheduled_for ${it.operator.opcode} ? AND ") }
                        conditions.message?.let { append("message = ? AND ") }

                        // Remove dangling "AND "
                        setLength(length - 4)
                    }

                    limit?.let { append(" LIMIT ?") }
                    offset?.let { append(" OFFSET ?") }
                }

            // The posistion of the question marks in the sql must be relative to the position in the statement
            var position = 1

            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                conditions.id?.let { stmt.setObject(position++, it) }
                conditions.kind?.let { stmt.setString(position++, it.name) }
                conditions.payload?.let { stmt.setString(position++, it) }
                conditions.attempt?.let { stmt.setObject(position++, it) }
                conditions.createdAt?.let { stmt.setTimestamp(position++, Timestamp.valueOf(it.time)) }
                conditions.updatedAt?.let { stmt.setTimestamp(position++, Timestamp.valueOf(it.time)) }
                conditions.scheduledFor?.let { stmt.setTimestamp(position++, Timestamp.valueOf(it.time)) }
                conditions.message?.let { stmt.setString(position++, it) }
                limit?.let { stmt.setInt(position++, it) }
                offset?.let { stmt.setInt(position++, it) }

                appLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from)
            }
        }

        suspend fun count(conditions: (where: Where) -> Unit = {}): Int {
            val conditions = Where().apply(conditions)
            val sql =
                buildString {
                    append("SELECT count(*) FROM $TABLE_NAME")
                    if (conditions.any()) {
                        append(" WHERE ")
                        conditions.id?.let { append("id = ? AND ") }
                        conditions.kind?.let { append("kind = ? AND ") }
                        conditions.payload?.let { append("payload = ? AND ") }
                        conditions.status?.let {
                            val statuses = it.joinToString(", ") { status -> "'$status'" }
                            append("status IN ($statuses) AND ")
                        }
                        conditions.attempt?.let { append("attempt = ? AND ") }
                        conditions.createdAt?.let { append("created_at ${it.operator.opcode} ? AND ") }
                        conditions.updatedAt?.let { append("updated_at ${it.operator.opcode} ? AND ") }
                        conditions.scheduledFor?.let { append("scheduled_for ${it.operator.opcode} ? AND ") }
                        conditions.message?.let { append("message = ? AND ") }

                        // Remove dangling "AND "
                        setLength(length - 4)
                    }
                }

            // The posistion of the question marks in the sql must be relative to the position in the statement
            var position = 1

            return coroutineContext.connection
                .prepareStatement(sql)
                .use { stmt ->
                    conditions.id?.let { stmt.setObject(position++, it) }
                    conditions.kind?.let { stmt.setString(position++, it.name) }
                    conditions.payload?.let { stmt.setString(position++, it) }
                    conditions.attempt?.let { stmt.setObject(position++, it) }
                    conditions.createdAt?.let { stmt.setTimestamp(position++, Timestamp.valueOf(it.time)) }
                    conditions.updatedAt?.let { stmt.setTimestamp(position++, Timestamp.valueOf(it.time)) }
                    conditions.scheduledFor?.let { stmt.setTimestamp(position++, Timestamp.valueOf(it.time)) }
                    conditions.message?.let { stmt.setString(position++, it) }

                    appLog.debug(sql)
                    secureLog.debug(stmt.toString())
                    stmt.executeQuery().map {
                        it.getInt(1)
                    }
                }.firstOrNull() ?: 0
        }
    }

    data class Where(
        var id: UUID? = null,
        var kind: Kind? = null,
        var payload: String? = null,
        var status: List<Status>? = null,
        var attempt: Int? = null,
        var createdAt: SelectTime? = null,
        var updatedAt: SelectTime? = null,
        var scheduledFor: SelectTime? = null,
        var message: String? = null,
    ) {
        fun any() =
            listOf(
                id,
                kind,
                payload,
                status,
                attempt,
                createdAt,
                updatedAt,
                scheduledFor,
                message,
            ).any { it != null }
    }
}

fun TaskDao.Companion.from(rs: ResultSet) =
    TaskDao(
        id = UUID.fromString(rs.getString("id")),
        kind = Kind.valueOf(rs.getString("kind")),
        payload = rs.getString("payload"),
        status = Status.valueOf(rs.getString("status")),
        attempt = rs.getInt("attempt"),
        createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
        updatedAt = rs.getTimestamp("updated_at").toLocalDateTime(),
        scheduledFor = rs.getTimestamp("scheduled_for").toLocalDateTime(),
        message = rs.getString("message"),
    )

/**
 * [column] [operator] [time]
 * eg: created_at >= now()
 */
data class SelectTime(
    val operator: Operator = Operator.LE,
    val time: LocalDateTime = LocalDateTime.now(),
)

enum class Operator(
    internal val opcode: String,
) {
    EQ("="),
    NEQ("!="),
    IN("IN"),
    GE(">="),
    LE("<="),
}
