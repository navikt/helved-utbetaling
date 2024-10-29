package oppdrag.iverksetting.domene

import no.nav.utsjekk.kontrakter.felles.Satstype

fun Satstype.tilOppdragskode(): String =
    when (this) {
        Satstype.DAGLIG -> "DAG"
        Satstype.DAGLIG_INKL_HELG -> "DAG7"
        Satstype.MÅNEDLIG -> "MND"
        Satstype.ENGANGS -> "ENG"
    }
