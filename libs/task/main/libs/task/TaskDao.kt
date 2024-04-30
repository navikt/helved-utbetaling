package libs.task

import libs.postgres.coroutines.connection
import libs.postgres.map
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.coroutineContext

typealias LastProcessedTime = LocalDateTime
typealias TriggeredBeforeTime = LocalDateTime

data class TaskDao(
    val payload: String,
    val type: String,
    val metadata: String,
    val avvikstype: String,
    val trigger_tid: LocalDateTime? = null,
    val id: UUID = UUID.randomUUID(),
    val status: Status = Status.UBEHANDLET,
    val opprettet_tid: LocalDateTime = LocalDateTime.now(),
    val versjon: Long = 0,
) {
    suspend fun insert() {
        coroutineContext.connection.prepareStatement(
            """
           INSERT INTO task (
                id, 
                payload, 
                status,
                versjon,
                opprettet_tid,
                type,
                metadata,
                trigger_tid,
                avvikstype 
           ) VALUES (?,?,?,?,?,?,?,?,?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, id)
            stmt.setString(2, payload)
            stmt.setString(3, status.name)
            stmt.setObject(4, versjon)
            stmt.setTimestamp(5, Timestamp.valueOf(opprettet_tid))
            stmt.setString(6, type)
            stmt.setString(7, metadata)
            stmt.setObject(8, trigger_tid?.let(Timestamp::valueOf))
            stmt.setString(9, avvikstype)
            stmt.executeUpdate()
        }
    }

    suspend fun update(status: Status) {
        coroutineContext.connection.prepareStatement(
            """
                UPDATE task
                SET status = ?
                WHERE id = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, status.name)
            stmt.setObject(2, id)
            stmt.executeUpdate()
        }
    }

    companion object {
        fun findBy(status: List<Status>, time: TriggeredBeforeTime, con: Connection): List<TaskDao> {
            TODO()
        }

        suspend fun findBy(status: Status): List<TaskDao> {
            return coroutineContext.connection.prepareStatement(
                """
                    SELECT * FROM task
                    WHERE status = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, status.name)
                stmt.executeQuery().map(::from)
            }
        }

        fun findBy(status: List<Status>): List<TaskDao> {
            TODO()
        }

        suspend fun countBy(status: List<Status>): Long {
            TODO()
        }

        fun findBy(status: List<Status>, type: String): List<TaskDao> {
            TODO()
        }

        fun findOne(payload: String, type: String): TaskDao? {
            TODO()
        }

        fun countOpenTasks(con: Connection): List<TaskDao.AntallÅpneTask> {
            TODO()
        }

        fun finnTasksSomErFerdigNåMenFeiletFør(con: Connection): List<TaskDao> {
            TODO()
        }

        fun findByCallId(callId: String): List<TaskDao> {
            TODO()
        }

        fun findBy(status: Status, time: LastProcessedTime): List<TaskDao> {
            TODO()
        }

        private fun from(rs: ResultSet): TaskDao =
            TaskDao(
                payload = rs.getString("payload"),
                type = rs.getString("type"),
                metadata = rs.getString("metadata"),
                avvikstype = rs.getString("avvikstype"),
                trigger_tid = rs.getTimestamp("trigger_tid")?.toLocalDateTime(),
                id = UUID.fromString(rs.getString("id")),
                status = Status.valueOf(rs.getString("status")),
                opprettet_tid = rs.getTimestamp("opprettet_tid").toLocalDateTime(),
                versjon = rs.getLong("versjon"),
            )
    }

    data class AntallÅpneTask(
        val type: String,
        val status: Status,
        val count: Long,
    )
}
