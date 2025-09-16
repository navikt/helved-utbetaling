package utsjekk.iverksetting.utbetalingsoppdrag

import utsjekk.iverksetting.AndelData
import java.time.LocalDate

private sealed interface BeståendeAndelResultat
private object NyAndelSkriverOver : BeståendeAndelResultat
private class Opphørsdato(val opphør: LocalDate) : BeståendeAndelResultat
private class AvkortAndel(val andel: AndelData, val opphør: LocalDate? = null) : BeståendeAndelResultat
internal data class BeståendeAndeler(val andeler: List<AndelData>, val opphørFra: LocalDate? = null)

internal object BeståendeAndelerBeregner {

    fun finnBeståendeAndeler(
        existing: List<AndelData>,
        requested: List<AndelData>,
        opphørsdato: LocalDate?,
    ): BeståendeAndeler {
        if (opphørsdato != null) return BeståendeAndeler(emptyList(), opphørsdato)
        val existingEnriched = existing.copyIdFrom(requested)
        val indexOnFirstDiff = existing.getIndexOnFirstDiff(requested) 
            ?: return BeståendeAndeler(existingEnriched, null) // normalt sett er det ny(e) andel(er) i kjeden som skal legges til  

        return when (val opphørsdato = finnBeståendeAndelOgOpphør(indexOnFirstDiff, existingEnriched, requested)) {
            is Opphørsdato -> BeståendeAndeler(existingEnriched.subList(0, indexOnFirstDiff), opphørsdato.opphør)
            is NyAndelSkriverOver -> BeståendeAndeler(existingEnriched.subList(0, indexOnFirstDiff))
            is AvkortAndel -> BeståendeAndeler(existingEnriched.subList(0, indexOnFirstDiff) + opphørsdato.andel, opphørsdato.opphør)
        }
    }

    private fun finnBeståendeAndelOgOpphør(
        indexOnFirstDiff: Int,
        existing: List<AndelData>,
        requested: List<AndelData>,
    ): BeståendeAndelResultat {
        val forrige = existing[indexOnFirstDiff]
        val ny = if (requested.size > indexOnFirstDiff) requested[indexOnFirstDiff] else null
        val nyNeste = if (requested.size > indexOnFirstDiff + 1) requested[indexOnFirstDiff + 1] else null
        return finnBeståendeAndelOgOpphør(ny, forrige, nyNeste)
    }

    private fun finnBeståendeAndelOgOpphør(
        ny: AndelData?,
        forrige: AndelData,
        nyNeste: AndelData?,
    ): BeståendeAndelResultat {
        if (ny == null || forrige.fom < ny.fom) return Opphørsdato(forrige.fom)
        if (forrige.fom > ny.fom || forrige.beløp != ny.beløp) {
            if (ny.beløp == 0) return Opphørsdato(ny.fom)
            return NyAndelSkriverOver
        }
        if (forrige.tom > ny.tom) {
            if (nyNeste != null && nyNeste.fom == ny.tom.plusDays(1) && nyNeste.beløp != 0) {
                return AvkortAndel(forrige.copy(tom = ny.tom), null)
            }
            return AvkortAndel(forrige.copy(tom = ny.tom), ny.tom.plusDays(1) )
        }
        return NyAndelSkriverOver
    }

    private fun List<AndelData>.copyIdFrom(other: List<AndelData>) = 
        this.mapIndexed { index, andel ->
            if (other.size <= index) andel // fallback
            else andel.copy(id = other[index].id) // ta ID fra requesten (random uuid)
        }

    private fun List<AndelData>.getIndexOnFirstDiff(other: List<AndelData>): Int? {
        this.forEachIndexed { index, andel ->
            if (other.size <= index) return index
            if (!andel.erLik(other[index])) return index
        }
        return null // alt er likt, other har fler elementer
    }

    private fun AndelData.erLik(other: AndelData): Boolean =
        this.fom == other.fom && this.tom == other.tom && this.beløp == other.beløp
}
