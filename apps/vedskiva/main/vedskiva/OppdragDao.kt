package vedskiva

import java.sql.ResultSet
import kotlin.coroutines.coroutineContext
import libs.jdbc.concurrency.connection
import libs.jdbc.map
import libs.jdbc.Dao
import java.sql.Timestamp
import libs.utils.logger
import libs.utils.secureLog
import java.time.LocalDateTime

data class OppdragDao(
    val nokkelAvstemming: LocalDateTime,
    val hashKey: Int,
    val kodeFagomraade: String,
    val personident: String,
    val fagsystemId: String,
    val lastDelytelseId: String,
    val tidspktMelding: LocalDateTime,
    val sats: Long,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val alvorlighetsgrad: String? = null,
    val kodeMelding: String? = null,
    val beskrMelding: String? = null,
) {
    companion object: Dao<OppdragDao> {
        override val table = "oppdrag"

        override fun from(rs: ResultSet) = OppdragDao(
            nokkelAvstemming = rs.getTimestamp("nokkelAvstemming").toLocalDateTime(),
            hashKey = rs.getInt("hash_key"),
            kodeFagomraade = rs.getString("kodeFagomraade"),
            personident = rs.getString("personident"),
            fagsystemId = rs.getString("fagsystemId"),
            lastDelytelseId = rs.getString("lastDelytelseId"),
            tidspktMelding = rs.getTimestamp("tidspktMelding").toLocalDateTime(),
            sats = rs.getLong("sats"),
            createdAt = rs.getTimestamp("createdAt").toLocalDateTime(),
            alvorlighetsgrad = rs.getString("alvorlighetsgrad"),
            kodeMelding = rs.getString("kodeMelding"),
            beskrMelding = rs.getString("beskrMelding"),
        )

        suspend fun selectWith(from: LocalDateTime, to: LocalDateTime): List<OppdragDao> {
            val sql = "SELECT * FROM  $table WHERE nokkelAvstemming >= ? AND nokkelAvstemming <= ?"
            return query(sql) { stmt ->
                stmt.setTimestamp(1, Timestamp.valueOf(from))
                stmt.setTimestamp(2, Timestamp.valueOf(to))
            }
        }
    }

    // add this if duplicate oppdrag wrongly occurs:
    // ON CONFLICT (hash_key) DO NOTHIN
    suspend fun insert(): Int {
        val sql = """
            INSERT INTO $table (
                nokkelAvstemming,
                hash_key,
                kodeFagomraade,
                personident,
                fagsystemId,
                lastDelytelseId,
                tidspktMelding,
                sats,
                createdAt,
                alvorlighetsgrad,
                kodeMelding,
                beskrMelding
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        return update(sql) { stmt ->
            stmt.setTimestamp(1, Timestamp.valueOf(nokkelAvstemming))
            stmt.setInt(2, hashKey)
            stmt.setString(3, kodeFagomraade)
            stmt.setString(4, personident)
            stmt.setString(5, fagsystemId)
            stmt.setString(6, lastDelytelseId)
            stmt.setTimestamp(7, Timestamp.valueOf(tidspktMelding))
            stmt.setLong(8, sats)
            stmt.setTimestamp(9, Timestamp.valueOf(createdAt))
            stmt.setString(10, alvorlighetsgrad)
            stmt.setString(11, kodeMelding)
            stmt.setString(12, beskrMelding)
        }
    }
}

