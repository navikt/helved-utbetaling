package peisschtappern

import libs.jdbc.Dao
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime

data class KjentDuplikat(
    val sakId: String,
    val fom: String,
    val tom: String,
    val fagsystem: String,
    val registrertAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object : Dao<KjentDuplikat> {
        override val table = "kjent_duplikat"

        override fun from(rs: ResultSet) = KjentDuplikat(
            sakId = rs.getString("sak_id"),
            fom = rs.getString("fom"),
            tom = rs.getString("tom"),
            fagsystem = rs.getString("fagsystem"),
            registrertAt = rs.getTimestamp("registrert_at").toLocalDateTime(),
        )
    }

    suspend fun insert(): Int {
        val sql = """
            INSERT INTO $table (sak_id, fom, tom, fagsystem, registrert_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (sak_id, fom, tom, fagsystem) DO NOTHING
        """.trimIndent()

        return update(sql) { stmt ->
            stmt.setString(1, sakId)
            stmt.setString(2, fom)
            stmt.setString(3, tom)
            stmt.setString(4, fagsystem)
            stmt.setTimestamp(5, Timestamp.valueOf(registrertAt))
        }
    }
}
