package libs.task

import libs.postgres.concurrency.connection
import libs.postgres.map
import libs.utils.appLog
import libs.utils.env
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.coroutineContext

private const val BRUKERNAVN_NÅR_SIKKERHETSKONTEKST_IKKE_FINNES = "VL"

data class TaskLogDao(
    val task_id: UUID,
    val type: Loggtype,
    val id: UUID = UUID.randomUUID(),
    val endret_av: String = BRUKERNAVN_NÅR_SIKKERHETSKONTEKST_IKKE_FINNES,
    val node: String = env("HOSTNAME", "node1"),
    val melding: String? = null,
    val opprettet_tid: LocalDateTime = LocalDateTime.now(),
) {
    suspend fun insert() {
        val sql = """
           INSERT INTO task_logg (id, task_id, type, node, opprettet_tid, endret_av, melding) 
           VALUES (?,?,?,?,?,?,?)
        """
        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, id)
            stmt.setObject(2, task_id)
            stmt.setString(3, type.name)
            stmt.setObject(4, node)
            stmt.setTimestamp(5, Timestamp.valueOf(opprettet_tid))
            stmt.setString(6, endret_av)
            stmt.setString(7, melding)
            stmt.executeUpdate()
        }.also {
            appLog.debug(sql)
        }
    }

    companion object {
        suspend fun findBy(task_id: UUID): List<TaskLogDao> = findBy(listOf(task_id))

        suspend fun findBy(task_ids: List<UUID>): List<TaskLogDao> {
            val sql = """
                SELECT * FROM task_logg
                WHERE task_id in (?)
            """
            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                val ids = task_ids.joinToString(", ") { it.toString() }
                stmt.setString(1, ids)
                stmt.executeQuery().map(::from)
            }.also {
                appLog.debug(sql)
            }
        }

        suspend fun findMetadataBy(task_ids: List<UUID>): List<TaskLoggMetadata> {
            val sql = """
                    SELECT task_id, count(*) antall_logger, MAX(opprettet_tid) siste_opprettet_tid,
                        (
                            SELECT melding FROM task_logg tl1
                            WHERE tl1.task_id = tl.task_id AND type='KOMMENTAR' ORDER BY tl1.opprettet_tid DESC LIMIT 1) 
                            siste_kommentar
                        )
                    FROM task_logg tl
                    WHERE task_id IN (?)
                    GROUP BY task_id
                """

            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                val ids = task_ids.joinToString(", ") { it.toString() }
                stmt.setString(1, ids)
                stmt.executeQuery().map(TaskLoggMetadata::from)
            }.also {
                appLog.debug(sql)
            }
        }

        suspend fun countBy(task_id: UUID, type: Loggtype): Long {
            val sql = """
                SELECT count(*) FROM task_logg
                WHERE task_id = ? AND type = ?
            """
            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, task_id)
                stmt.setString(2, type.name)
                stmt.executeQuery().map { it.getLong(1) }.single()
            }.also {
                appLog.debug(sql)
            }
        }

        suspend fun deleteBy(task_id: UUID) = deleteBy(listOf(task_id))

        suspend fun deleteBy(task_ids: List<UUID>): Int {
            val sql = """
                DELETE FROM task_logg
                WHERE task_id in (?)
            """
            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                val ids = task_ids.joinToString(", ") { it.toString() }
                stmt.setString(1, ids)
                stmt.executeUpdate()
            }.also {
                appLog.debug(sql)
            }
        }
    }
}

fun TaskLogDao.Companion.from(rs: ResultSet) = TaskLogDao(
    task_id = UUID.fromString(rs.getString("task_id")),
    type = Loggtype.valueOf(rs.getString("type")),
    id = UUID.fromString(rs.getString("id")),
    endret_av = rs.getString("endret_av"),
    node = rs.getString("node"),
    melding = rs.getString("melding"),
    opprettet_tid = rs.getTimestamp("opprettet_tid").toLocalDateTime()
)

class TaskLoggMetadata(
    val task_id: UUID,
    val antall_logger: Int,
    val siste_opprettet_tid: LocalDateTime,
    val siste_kommentar: String?,
) {
    companion object {
        fun from(rs: ResultSet) = TaskLoggMetadata(
            task_id = UUID.fromString(rs.getString("task_id")),
            antall_logger = rs.getInt("antall_logger"),
            siste_opprettet_tid = rs.getTimestamp("siste_opprettet_tid").toLocalDateTime(),
            siste_kommentar = rs.getString("siste_kommentar")
        )
    }
}
