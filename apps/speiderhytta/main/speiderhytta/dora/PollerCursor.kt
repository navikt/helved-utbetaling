package speiderhytta.dora

import libs.jdbc.Dao
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/**
 * Cursor table used by pollers to remember the highest timestamp they've
 * already processed. Persisted so restarts don't re-fetch the entire history.
 */
data class PollerCursor(
    val poller: String,
    val lastSeenTs: Instant,
    val lastEtag: String? = null,
) {
    companion object : Dao<PollerCursor> {
        override val table = "poller_cursor"

        override fun from(rs: ResultSet) = PollerCursor(
            poller = rs.getString("poller"),
            lastSeenTs = rs.getTimestamp("last_seen_ts").toInstant(),
            lastEtag = rs.getString("last_etag"),
        )

        suspend fun load(poller: String): PollerCursor? {
            val sql = "SELECT * FROM $table WHERE poller = ? LIMIT 1"
            return query(sql) { stmt -> stmt.setString(1, poller) }.firstOrNull()
        }
    }

    suspend fun save(): Int {
        val sql = """
            INSERT INTO $table (poller, last_seen_ts, last_etag)
            VALUES (?, ?, ?)
            ON CONFLICT (poller) DO UPDATE SET
                last_seen_ts = EXCLUDED.last_seen_ts,
                last_etag    = EXCLUDED.last_etag
        """.trimIndent()
        return update(sql) { stmt ->
            stmt.setString(1, poller)
            stmt.setTimestamp(2, Timestamp.from(lastSeenTs))
            stmt.setString(3, lastEtag)
        }
    }
}
