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

data class TaskHistoryDao(
    val id: UUID = UUID.randomUUID(),
    val taskId: UUID,
    val createdAt: LocalDateTime,
    val triggeredAt: LocalDateTime,
    val triggeredBy: LocalDateTime,
    val status: Status,
) {
    suspend fun insert() {
        val sql = """
            INSERT INTO $TABLE_NAME  (id, task_id, created_at, triggered_at, triggered_by, status) 
            VALUES (?,?,?,?,?,?)
        """.trimIndent()
        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, id)
            stmt.setObject(2, taskId)
            stmt.setTimestamp(3, Timestamp.valueOf(createdAt))
            stmt.setTimestamp(4, Timestamp.valueOf(triggeredAt))
            stmt.setTimestamp(5, Timestamp.valueOf(triggeredBy))
            stmt.setString(6, status.name)

            appLog.debug(sql)
            secureLog.debug(stmt.toString())
            stmt.executeUpdate()
        }
    }

    companion object {
        const val TABLE_NAME = "task_v2_history"

        suspend fun select(taskId: UUID): List<TaskHistoryDao> {
            val sql = "SELECT * FROM $TABLE_NAME WHERE task_id = ?"

            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, taskId)

                appLog.debug(sql)
                secureLog.debug(stmt.toString())
                stmt.executeQuery().map(::from)
            }
        }
    }
}

fun TaskHistoryDao.Companion.from(rs: ResultSet) = TaskHistoryDao(
    id = UUID.fromString(rs.getString("id")),
    taskId = UUID.fromString(rs.getString("task_id")),
    createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
    triggeredAt = rs.getTimestamp("triggered_at").toLocalDateTime(),
    triggeredBy = rs.getTimestamp("triggered_by").toLocalDateTime(),
    status = Status.valueOf(rs.getString("status")),
)
