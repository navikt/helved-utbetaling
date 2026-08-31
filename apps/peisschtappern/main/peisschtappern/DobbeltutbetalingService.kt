@file:UseSerializers(libs.kotlinx.LocalDateSerializer::class)

package peisschtappern

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import libs.kotlinx.KotlinxJson
import models.StatusReply
import java.time.LocalDate

object DobbeltutbetalingService {

    suspend fun finnUslukkede(fom: Long, tom: Long): List<Suspect> {
        val statuses = Daos.findStatuses("OK", fom, tom)
        val slukkede = KjentDobbeltutbetaling.findAll()
            .filter { it.slukketAt != null || it.håndtertAt != null }
            .map { it.key }
            .toSet()
        return finn(statuses).filterNot { it.key in slukkede }
    }

    suspend fun finnUhåndterte(fom: Long, tom: Long): List<Suspect> {
        val statuses = Daos.findStatuses("OK", fom, tom)
        val håndterte = KjentDobbeltutbetaling.findAll()
            .filter { it.håndtertAt != null }
            .map { it.key }
            .toSet()
        return finn(statuses).filterNot { it.key in håndterte }
    }

    fun finn(daos: List<Daos>): List<Suspect> {
        val suspects: MutableMap<String, Suspect> = mutableMapOf()
        for (suspect in daos.filter { it.value != null }) {
            val status: StatusReply = KotlinxJson.decodeFromString(suspect.value!!)
            val lines = status.detaljer?.linjer ?: emptyList()

            for (line in lines.filter { it.beløp > 0u }) {
                val kandidat = Suspect(
                    behandlingId = line.behandlingId,
                    klassekode = line.klassekode,
                    fom = line.fom,
                    tom = line.tom,
                    beløp = line.beløp,
                    kilder = mutableMapOf(),
                    sakId = suspect.sakId,
                    fagsystem = suspect.fagsystem,
                )
                val suspectGroup = suspects.getOrPut(kandidat.key) { kandidat }

                suspectGroup.kilder["${suspect.key}::${suspect.partition}::${suspect.offset}"] = Suspect.Kilde(
                    key = suspect.key,
                    partition = suspect.partition,
                    offset = suspect.offset,
                    timestampMs = suspect.system_time_ms,
                )
            }
        }

        return suspects.values.toList().filter { it.kilder.size > 1 }
    }
}

@Serializable
data class Suspect(
    val behandlingId: String,
    val klassekode: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: UInt,
    val kilder: MutableMap<String, Kilde>,
    val sakId: String? = null,
    val fagsystem: String? = null,
) {
    val key: String get() = "$behandlingId::$klassekode::$fom::$tom"

    @Serializable
    data class Kilde(
        val key: String,
        val partition: Int,
        val offset: Long,
        val timestampMs: Long,
    )
}
