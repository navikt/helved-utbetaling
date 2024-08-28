package utsjekk.iverksetting.utbetalingsoppdrag

import utsjekk.iverksetting.AndelData
import java.time.LocalDate

private sealed interface BeståendeAndelResultat

private object NyAndelSkriverOver : BeståendeAndelResultat

private class Opphørsdato(val opphør: LocalDate) : BeståendeAndelResultat

private class AvkortAndel(val andel: AndelData, val opphør: LocalDate? = null) : BeståendeAndelResultat

internal data class BeståendeAndeler(
    val andeler: List<AndelData>,
    val opphørFra: LocalDate? = null,
)

internal object BeståendeAndelerBeregner {
    fun finnBeståendeAndeler(
        forrigeAndeler: List<AndelData>,
        nyeAndeler: List<AndelData>,
        opphørsdato: LocalDate?,
    ): BeståendeAndeler {
        // Når det sendes med ett opphørsdato beholder vi ingen andeler fra forrige behandling
        if (opphørsdato != null) {
            return BeståendeAndeler(emptyList(), opphørsdato)
        }

        val indexPåFørsteEndring = finnIndexPåFørsteEndring(forrigeAndeler, nyeAndeler)
        val forrigeAndelerMedOppdatertId = oppdaterBeståendeAndelerMedId(forrigeAndeler, nyeAndeler)
        return finnBeståendeAndeler(forrigeAndelerMedOppdatertId, nyeAndeler, indexPåFørsteEndring)
    }

    private fun finnBeståendeAndeler(
        forrigeAndeler: List<AndelData>,
        nyeAndeler: List<AndelData>,
        indexPåFørsteEndring: Int?,
    ): BeståendeAndeler {
        return if (indexPåFørsteEndring != null) {
            finnBeståendeAndelerNårDetFinnesEndring(
                forrigeAndeler = forrigeAndeler,
                nyeAndeler = nyeAndeler,
                indexPåFørsteEndring = indexPåFørsteEndring,
            )
        } else {
            BeståendeAndeler(forrigeAndeler, null)
        }
    }

    fun finnBeståendeAndelerNårDetFinnesEndring(
        forrigeAndeler: List<AndelData>,
        nyeAndeler: List<AndelData>,
        indexPåFørsteEndring: Int,
    ): BeståendeAndeler {
        val opphørsdato = finnBeståendeAndelOgOpphør(indexPåFørsteEndring, forrigeAndeler, nyeAndeler)
        return when (opphørsdato) {
            is Opphørsdato -> BeståendeAndeler(forrigeAndeler.subList(0, indexPåFørsteEndring), opphørsdato.opphør)
            is NyAndelSkriverOver -> BeståendeAndeler(forrigeAndeler.subList(0, maxOf(0, indexPåFørsteEndring)))
            is AvkortAndel -> {
                val avkortetAndeler = forrigeAndeler.subList(0, maxOf(0, indexPåFørsteEndring))
                BeståendeAndeler(avkortetAndeler + opphørsdato.andel, opphørsdato.opphør)
            }
        }
    }

    private fun finnBeståendeAndelOgOpphør(
        index: Int,
        forrigeAndeler: List<AndelData>,
        nyeAndeler: List<AndelData>,
    ): BeståendeAndelResultat {
        val forrige = forrigeAndeler[index]
        val ny = if (nyeAndeler.size > index) nyeAndeler[index] else null
        val nyNeste = if (nyeAndeler.size > index + 1) nyeAndeler[index + 1] else null

        return finnBeståendeAndelOgOpphør(ny, forrige, nyNeste)
    }

    private fun finnBeståendeAndelOgOpphør(
        ny: AndelData?,
        forrige: AndelData,
        nyNeste: AndelData?,
    ): BeståendeAndelResultat {
        if (ny == null || forrige.fom < ny.fom) {
            return Opphørsdato(forrige.fom)
        }
        if (forrige.fom > ny.fom || forrige.beløp != ny.beløp) {
            if (ny.beløp == 0) {
                return Opphørsdato(ny.fom)
            }
            return NyAndelSkriverOver
        }
        if (forrige.tom > ny.tom) {
            val opphørsdato =
                if (nyNeste == null || nyNeste.fom != ny.tom.plusDays(1) || nyNeste.beløp == 0) {
                    ny.tom.plusDays(1)
                } else {
                    null
                }
            return AvkortAndel(forrige.copy(tom = ny.tom), opphørsdato)
        }
        return NyAndelSkriverOver
    }

    /**
     * Oppdaterer bestående andeler med id for då er en del av resultatet, uten å oppdatere de med periodeId/forrigePeriodeId
     */
    private fun oppdaterBeståendeAndelerMedId(
        forrigeAndeler: List<AndelData>,
        nyeAndeler: List<AndelData>,
    ) = forrigeAndeler.mapIndexed { forrigeIndex, andelData ->
        if (nyeAndeler.size > forrigeIndex) {
            andelData.copy(id = nyeAndeler[forrigeIndex].id)
        } else {
            andelData
        }
    }

    private fun finnIndexPåFørsteEndring(
        forrige: List<AndelData>,
        nye: List<AndelData>,
    ): Int? {
        forrige.forEachIndexed { index, andelData ->
            if (nye.size > index) {
                val nyAndelForIndex = nye[index]
                if (!andelData.erLik(nyAndelForIndex)) {
                    return index
                }
            } else {
                return index
            }
        }
        return null
    }

    private fun AndelData.erLik(other: AndelData): Boolean =
        this.fom == other.fom &&
            this.tom == other.tom &&
            this.beløp == other.beløp
}
