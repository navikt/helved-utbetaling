package libs.task

import libs.postgres.concurrency.connection
import libs.postgres.map
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.coroutineContext

typealias BeforeOpprettetTid = LocalDateTime
typealias BeforeTriggerTid = LocalDateTime

data class TaskDao(
    val payload: String,
    val type: String,
    val metadata: String?,
    val avvikstype: String?,
    val trigger_tid: LocalDateTime = LocalDateTime.now(),
    val id: UUID = UUID.randomUUID(),
    val status: Status = Status.UBEHANDLET,
    val opprettet_tid: LocalDateTime = LocalDateTime.now(),
    val versjon: Long = 0,
) {
    suspend fun insert() {
        coroutineContext.connection.prepareStatement(
            """
           INSERT INTO task (id, payload, status, versjon, opprettet_tid, type, metadata, trigger_tid, avvikstype) 
           VALUES (?,?,?,?,?,?,?,?,?)
            """
        ).use { stmt ->
            stmt.setObject(1, id)
            stmt.setString(2, payload)
            stmt.setString(3, status.name)
            stmt.setObject(4, versjon)
            stmt.setTimestamp(5, Timestamp.valueOf(opprettet_tid))
            stmt.setString(6, type)
            stmt.setString(7, metadata)
            stmt.setObject(8, Timestamp.valueOf(trigger_tid))
            stmt.setString(9, avvikstype)
            stmt.executeUpdate()
        }
    }

    suspend fun update(status: Status) {
        coroutineContext.connection.prepareStatement(
            "UPDATE task SET status = ? WHERE id = ?"
        ).use { stmt ->
            stmt.setString(1, status.name)
            stmt.setObject(2, id)
            stmt.executeUpdate()
        }
    }

    companion object {
        suspend fun findBy(status: List<Status>, trigger_tid: BeforeTriggerTid): List<TaskDao> =
            coroutineContext.connection.prepareStatement(
                """
                   SELECT * FROM task
                    WHERE status in (?) and trigger_tid < ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, status.joinToString(", ") { it.name })
                stmt.setObject(2, Timestamp.valueOf(trigger_tid))
                stmt.executeQuery().map(::from)
            }

        suspend fun findBy(status: Status): List<TaskDao> =
            coroutineContext.connection.prepareStatement(
                """
                   SELECT * FROM task
                   WHERE status = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, status.name)
                stmt.executeQuery().map(::from)
            }

        suspend fun findBy(status: List<Status>): List<TaskDao> =
            coroutineContext.connection.prepareStatement(
                """
                    SELECT * FROM task
                    WHERE status in (?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, status.joinToString(", ") { it.name })
                stmt.executeQuery().map(::from)
            }

        suspend fun countBy(status: List<Status>): Long =
            coroutineContext.connection.prepareStatement(
                """
                    SELECT count(*) FROM task
                    WHERE status in (?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, status.joinToString(", ") { it.name })
                stmt.executeQuery().map { it.getLong(1) }.single()
            }

        suspend fun findBy(status: List<Status>, type: String): List<TaskDao> =
            coroutineContext.connection.prepareStatement(
                """
                    SELECT * FROM task
                    WHERE status in (?) and type = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, status.joinToString(", ") { it.name })
                stmt.setString(2, type)
                stmt.executeQuery().map(::from)
            }

        suspend fun findFirstOrNullBy(payload: String, type: String): TaskDao? =
            coroutineContext.connection.prepareStatement(
                """
                    SELECT * FROM task
                    WHERE payload = ? and type = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, payload)
                stmt.setString(2, type)
                stmt.executeQuery().map(::from).firstOrNull()
            }

        suspend fun countOpenTasks(): List<AntallÅpneTask> =
            coroutineContext.connection.prepareStatement(
                """
                    SELECT t.type, t.status, count(*) AS count
                    FROM task t
                    WHERE t.status IN (?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, Status.open().joinToString(", ") { it.name })
                stmt.executeQuery().map(AntallÅpneTask::from)
            }

//        suspend fun finnTasksSomErFerdigNåMenFeiletFør(): List<TaskDao> =
//            coroutineContext.connection.prepareStatement(
//                """
//                SELECT distinct t.*
//                FROM task t
//                JOIN task_logg l on t.id = l.task_id
//                WHERE t.status = ? and l.type in (?)
//            """.trimIndent()
//            ).use { stmt ->
//                stmt.setString(1, Status.FERDIG.name)
//                stmt.setString(2, listOf(Status.FEILET, Status.MANUELL_OPPFØLGING).joinToString(", ") { it.name })
//                stmt.executeQuery().map(::from)
//            }

//        suspend fun findBy(status: Status, opprettet_tid: BeforeOpprettetTid): List<TaskDao> =
//            coroutineContext.connection.prepareStatement(
//                """
//                    WITH q AS (
//                        SELECT t.id, l.type, l.opprettet_tid, row_number() OVER (PARTITION BY t.id ORDER BY l.opprettet_tid DESC) rn
//                        FROM task t
//                        JOIN task_logg l on t.id = l.task_id
//                        WHERE t.status = ?
//                    )
//                    SELECT t.*
//                    FROM task t JOIN q t2 on t.id = t2.id
//                    WHERE t2.rn = 1 AND t2.opprettet_tid < ?
//                """.trimIndent()
//            ).use { stmt ->
//                stmt.setString(1, status.name)
//                stmt.setObject(2, Timestamp.valueOf(opprettet_tid))
//                stmt.executeQuery().map(::from)
//            }

        suspend fun findBy(x_trace_id: String): List<TaskDao> = TODO()
    }
}

fun TaskDao.Companion.from(rs: ResultSet) = TaskDao(
    payload = rs.getString("payload"),
    type = rs.getString("type"),
    metadata = rs.getString("metadata"),
    avvikstype = rs.getString("avvikstype"),
    trigger_tid = rs.getTimestamp("trigger_tid").toLocalDateTime(),
    id = UUID.fromString(rs.getString("id")),
    status = Status.valueOf(rs.getString("status")),
    opprettet_tid = rs.getTimestamp("opprettet_tid").toLocalDateTime(),
    versjon = rs.getLong("versjon"),
)

data class AntallÅpneTask(
    val type: String,
    val status: Status,
    val count: Long,
) {
    companion object {
        fun from(rs: ResultSet) = AntallÅpneTask(
            type = rs.getString("type"),
            status = rs.getString("status").let(Status::valueOf),
            count = rs.getLong("count")
        )
    }
}
