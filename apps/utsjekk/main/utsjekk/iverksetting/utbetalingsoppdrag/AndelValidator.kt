package utsjekk.iverksetting.utbetalingsoppdrag

import models.badRequest
import models.kontrakter.felles.Satstype
import utsjekk.iverksetting.AndelData
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

internal object AndelValidator {
    fun validerAndeler(
        forrige: List<AndelData>,
        nye: List<AndelData>,
    ) {
        if ((forrige + nye).any { it.beløp == 0 }) {
            error("Andeler inneholder 0-beløp")
        }

        val alleIder = forrige.map { it.id } + nye.map { it.id }
        if (alleIder.size != alleIder.toSet().size) {
            error("Inneholder duplikat av id'er")
        }

        forrige
            .filter { it.periodeId == null }
            .forEach { error("Tidligere andel=${it.id} mangler periodeId") }

        nye
            .filter { it.periodeId != null || it.forrigePeriodeId != null }
            .forEach { error("Ny andel=${it.id} inneholder periodeId/forrigePeriodeId") }

        (forrige + nye).map { validerSatstype(it) }
    }

    private fun validerSatstype(andelData: AndelData) {
        when (andelData.satstype) {
            Satstype.MÅNEDLIG -> validerMånedssats(andelData)
            else -> return
        }
    }

    private fun validerMånedssats(andelData: AndelData) {
        if (andelData.fom.dayOfMonth != 1 || andelData.tom != andelData.tom.sisteDagIMåneden()) {
            badRequest("Utbetalinger med satstype ${andelData.satstype.name} må starte den første i måneden og slutte den siste i måneden")
        }
    }

    private fun LocalDate.sisteDagIMåneden(): LocalDate {
        val defaultZoneId = ZoneId.systemDefault()
        val calendar = Calendar.getInstance().apply {
            time = Date.from(this@sisteDagIMåneden.atStartOfDay(defaultZoneId).toInstant())
            set(Calendar.DAY_OF_MONTH, this.getActualMaximum(Calendar.DAY_OF_MONTH))
        }

        return LocalDate.ofInstant(calendar.toInstant(), defaultZoneId)
    }
}
