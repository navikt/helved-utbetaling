package oppdrag.iverksetting.domene

import no.nav.utsjekk.kontrakter.felles.Satstype

fun Satstype.tilOppdragskode(): String =
    when (this) {
        Satstype.DAGLIG -> "DAG"
        Satstype.MÅNEDLIG -> "MND"
        Satstype.ENGANGS -> "ENG"
    }
