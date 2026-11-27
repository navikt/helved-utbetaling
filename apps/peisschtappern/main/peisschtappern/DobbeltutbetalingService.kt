@file:UseSerializers(libs.kotlinx.LocalDateSerializer::class)

package peisschtappern

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import libs.kotlinx.KotlinxJson
import models.StatusReply
import java.time.LocalDate

object DobbeltutbetalingService {

    suspend fun finnUslukkede(fom: Long, tom: Long): List<Suspect> {
        val slukkede = KjentDobbeltutbetaling.findAll()
            .filter { it.slukketAt != null || it.håndtertAt != null }
            .map { it.key }
            .toSet()
        return finnOk(fom, tom).filterNot { it.key in slukkede }
    }

    suspend fun finnUhåndterte(fom: Long, tom: Long): List<Suspect> {
        val håndterte = KjentDobbeltutbetaling.findAll()
            .filter { it.håndtertAt != null }
            .map { it.key }
            .toSet()
        return finnOk(fom, tom).filterNot { it.key in håndterte }
    }

    fun finn(daos: List<Daos>): List<Suspect> =
        SuspectCollector().apply { daos.forEach(::add) }.duplikater()

    private suspend fun finnOk(fom: Long, tom: Long): List<Suspect> {
        val collector = SuspectCollector()
        Daos.forEachStatus("OK", fom, tom, collector::add)
        return collector.duplikater()
    }

    private class SuspectCollector {
        private val suspects = mutableMapOf<String, Suspect>()

        fun add(status: Daos) {
            val value = status.value ?: return
            val reply: StatusReply = KotlinxJson.decodeFromString(value)
            val lines = reply.detaljer?.linjer ?: emptyList()

            for (line in lines.filter { it.beløp > 0u }) {
                val kandidat = Suspect(
                    behandlingId = line.behandlingId,
                    klassekode = line.klassekode,
                    fom = line.fom,
                    tom = line.tom,
                    beløp = line.beløp,
                    kilder = mutableMapOf(),
                    sakId = status.sakId,
                    fagsystem = status.fagsystem,
                )
                val suspectGroup = suspects.getOrPut(kandidat.key) { kandidat }

                suspectGroup.kilder["${status.key}::${status.partition}::${status.offset}"] = Suspect.Kilde(
                    key = status.key,
                    partition = status.partition,
                    offset = status.offset,
                    timestampMs = status.system_time_ms,
                )
            }
        }

        fun duplikater(): List<Suspect> = suspects.values.filter { it.kilder.size > 1 }
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
