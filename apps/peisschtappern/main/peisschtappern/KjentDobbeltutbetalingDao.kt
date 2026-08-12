package peisschtappern

import libs.jdbc.Dao
import java.sql.Date
import java.sql.ResultSet
import java.time.LocalDate

data class KjentDobbeltutbetaling(
    val behandlingId: String,
    val klassekode: String,
    val fom: LocalDate,
    val tom: LocalDate,
) {
    val key: String get() = "$behandlingId::$klassekode::$fom::$tom"

    companion object : Dao<KjentDobbeltutbetaling> {
        override val table = "kjent_dobbeltutbetaling"

        override fun from(rs: ResultSet) = KjentDobbeltutbetaling(
            behandlingId = rs.getString("behandling_id"),
            klassekode = rs.getString("klassekode"),
            fom = rs.getDate("fom").toLocalDate(),
            tom = rs.getDate("tom").toLocalDate(),
        )

        suspend fun findAll(): List<KjentDobbeltutbetaling> =
            query("SELECT * FROM $table")
    }

    suspend fun insert(): Int {
        val sql = """
            INSERT INTO $table (behandling_id, klassekode, fom, tom)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (behandling_id, klassekode, fom, tom) DO NOTHING
        """.trimIndent()

        return update(sql) { stmt ->
            stmt.setString(1, behandlingId)
            stmt.setString(2, klassekode)
            stmt.setDate(3, Date.valueOf(fom))
            stmt.setDate(4, Date.valueOf(tom))
        }
    }
}
