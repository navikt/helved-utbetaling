package models

import java.time.LocalDate

data class Simulering(
    val perioder: List<Simuleringsperiode>,
)

data class Simuleringsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val utbetalinger: List<SimulertUtbetaling>
)

data class SimulertUtbetaling(
    val fagsystem: Fagsystem,
    val sakId: String,
    val utbetalesTil: String,
    val stønadstype: Stønadstype,
    val tidligereUtbetalt: Int,
    val nyttBeløp: Int,
)


