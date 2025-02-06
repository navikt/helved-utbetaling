package utsjekk.utbetaling.simulering

import utsjekk.utbetaling.FagsystemDto
import utsjekk.utbetaling.Stønadstype
import java.time.LocalDate

data class SimuleringApi(
    val perioder: List<Simuleringsperiode>
)

data class Simuleringsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val utbetalinger: List<SimulertUtbetaling>
)

data class SimulertUtbetaling(
    val fagsystem: FagsystemDto,
    val sakId: String,
    val utbetalesTil: String,
    val stønadstype: Stønadstype,
    val tidligereUtbetalt: Int,
    val nyttBeløp: Int,
)