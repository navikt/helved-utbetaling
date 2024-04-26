package utsjekk.task

import java.sql.Connection
import java.time.LocalDateTime

typealias LastProcessedTime = LocalDateTime
typealias TriggeredBeforeTime = LocalDateTime

object TaskDao {

    suspend fun findBy(status: List<Status>, time: TriggeredBeforeTime, con: Connection): List<Task> {
        TODO()
    }

    suspend fun findBy(status: Status, con: Connection): List<Task> {
        TODO()
    }

    suspend fun findBy(status: List<Status>, con: Connection): List<Task> {
        TODO()
    }

    suspend fun countBy(status: List<Status>, con: Connection): Long {
        TODO()
    }

    suspend fun findBy(status: List<Status>, type: String, con: Connection): List<Task> {
        TODO()
    }

    suspend fun findOne(payload: String, type: String, con: Connection): Task? {
        TODO()
    }

    suspend fun countOpenTasks(con: Connection): List<AntallÅpneTask> {
        TODO()
    }

    suspend fun finnTasksSomErFerdigNåMenFeiletFør(con: Connection): List<Task> {
        TODO()
    }

    suspend fun findByCallId(callId: String, con: Connection): List<Task> {
        TODO()
    }

    suspend fun findBy(status: Status, time: LastProcessedTime, con: Connection): List<Task> {
        TODO()
    }

    data class Task(
        val id: Long,
        val payload: String,
        val status: Status,
        val versjon: Long,
        val opprettet_tid: LocalDateTime,
        val type: String,
        val metadata: String,
        val trigger_TID: LocalDateTime,
        val avvikstype: String,
    )
//
//    enum class Status {
//        AVVIKSHÅNDTERT,
//        BEHANDLER,
//        FEILET,
//        FERDIG,
//        KLAR_TIL_PLUKK,
//        MANUELL_OPPFØLGING,
//        PLUKKET,
//        UBEHANDLET
//    }

    data class AntallÅpneTask(
        val type: String,
        val status: Status,
        val count: Long,
    )
}
