package peisschtappern

import libs.jdbc.Dao
import java.sql.Date
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime

data class KjentDobbeltutbetaling(
    val behandlingId: String,
    val klassekode: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val slukketAt: LocalDateTime? = null,
    val håndtertAt: LocalDateTime? = null,
) {
    val key: String get() = "$behandlingId::$klassekode::$fom::$tom"

    companion object : Dao<KjentDobbeltutbetaling> {
        override val table = "kjent_dobbeltutbetaling"

        override fun from(rs: ResultSet) = KjentDobbeltutbetaling(
            behandlingId = rs.getString("behandling_id"),
            klassekode = rs.getString("klassekode"),
            fom = rs.getDate("fom").toLocalDate(),
            tom = rs.getDate("tom").toLocalDate(),
            slukketAt = rs.getTimestamp("slukket_at")?.toLocalDateTime(),
            håndtertAt = rs.getTimestamp("handtert_at")?.toLocalDateTime(),
        )

        suspend fun findAll(): List<KjentDobbeltutbetaling> =
            query("SELECT * FROM $table")
    }

    suspend fun slukk(): Int = upsert("slukket_at")

    suspend fun håndter(): Int = upsert("handtert_at")

    private suspend fun upsert(columnTimestamp: String): Int {
        val sql = """
            INSERT INTO $table (behandling_id, klassekode, fom, tom, $columnTimestamp)
            VALUES (?, ?, ?, ?, now())
            ON CONFLICT (behandling_id, klassekode, fom, tom)
            DO UPDATE SET $columnTimestamp = EXCLUDED.$columnTimestamp
            WHERE $table.$columnTimestamp IS NULL
        """.trimIndent()

        return update(sql) { stmt ->
            stmt.setString(1, behandlingId)
            stmt.setString(2, klassekode)
            stmt.setDate(3, Date.valueOf(fom))
            stmt.setDate(4, Date.valueOf(tom))
        }
    }
}
