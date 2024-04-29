package libs.task

import libs.postgres.coroutines.connection
import java.sql.Connection
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

    companion object {
        fun findBy(status: List<Status>, time: TriggeredBeforeTime, con: Connection): List<TaskDao> {
            TODO()
        }

        fun findBy(status: Status, con: Connection): List<TaskDao> {
            TODO()
        }

        fun findBy(status: List<Status>, con: Connection): List<TaskDao> {
            TODO()
        }

        fun countBy(status: List<Status>, con: Connection): Long {
            TODO()
        }

        fun findBy(status: List<Status>, type: String, con: Connection): List<TaskDao> {
            TODO()
        }

        fun findOne(payload: String, type: String, con: Connection): TaskDao? {
            TODO()
        }

        fun countOpenTasks(con: Connection): List<TaskDao.AntallÅpneTask> {
            TODO()
        }

        fun finnTasksSomErFerdigNåMenFeiletFør(con: Connection): List<TaskDao> {
            TODO()
        }

        fun findByCallId(callId: String, con: Connection): List<TaskDao> {
            TODO()
        }

        fun findBy(status: Status, time: LastProcessedTime, con: Connection): List<TaskDao> {
            TODO()
        }
    }

    data class AntallÅpneTask(
        val type: String,
        val status: Status,
        val count: Long,
    )
}
