package oppdrag.iverksetting.domene

import no.trygdeetaten.skjema.oppdrag.TfradragTillegg
import java.time.LocalDate

object OppdragSkjemaConstants {
    val OPPDRAG_GJELDER_DATO_FOM: LocalDate = LocalDate.of(2000, 1, 1)
    const val KODE_AKSJON = "1"

    const val ENHET_TYPE_BOSTEDSENHET = "BOS"
    const val ENHET_TYPE_BEHANDLENDE_ENHET = "BEH"
    const val ENHET = "8020"
    val ENHET_FOM: LocalDate = LocalDate.of(1900, 1, 1)
    val BRUKERS_NAVKONTOR_FOM: LocalDate = LocalDate.of(1970, 1, 1)

    val FRADRAG_TILLEGG = TfradragTillegg.T
    const val BRUK_KJØREPLAN_DEFAULT = "N"
}
