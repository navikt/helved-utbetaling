package abetal.models

import java.time.LocalDate
import no.trygdeetaten.skjema.oppdrag.*
import models.*

enum class Fagsystem {
    DP,
    TILTPENG,
    TILLST,
    AAP;

    companion object {
        fun from(stønad: Stønadstype) = when (stønad) {
            is StønadTypeDagpenger -> DP
            is StønadTypeTiltakspenger -> TILTPENG
            is StønadTypeTilleggsstønader -> TILLST
            is StønadTypeAAP -> AAP
        }
    }
}

enum class Endringskode {
    NY,
    ENDR,
}

enum class Kvitteringstatus(val kode: String) {
    OK("00"),
    MED_MANGLER("04"), // MED INFORMASJON
    FUNKSJONELL_FEIL("08"),
    TEKNISK_FEIL("12"),
    UKJENT("Ukjent");
}

enum class Utbetalingsfrekvens(val kode: String) {
    DAGLIG("DAG"),
    UKENTLIG("UKE"),
    MÅNEDLIG("MND"),
    DAGLIG_14("14DG"),
    ENGANGSUTBETALING("ENG"),
}

object OppdragSkjemaConstants {
    val OPPDRAG_GJELDER_DATO_FOM: LocalDate = LocalDate.of(2000, 1, 1)
    val BRUKERS_NAVKONTOR_FOM: LocalDate = LocalDate.of(1970, 1, 1)
    val ENHET_FOM: LocalDate = LocalDate.of(1900, 1, 1)
    val FRADRAG_TILLEGG = TfradragTillegg.T
    const val KODE_AKSJON = "1"
    const val ENHET_TYPE_BOSTEDSENHET = "BOS"
    const val ENHET_TYPE_BEHANDLENDE_ENHET = "BEH"
    const val ENHET = "8020"
    const val BRUK_KJØREPLAN_DEFAULT = "N"
}
