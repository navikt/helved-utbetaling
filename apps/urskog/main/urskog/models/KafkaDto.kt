package urskog.models

import java.time.LocalDate
import com.fasterxml.jackson.annotation.JsonValue

data class SimuleringResponseDto(
    val gjelderId: String,
    val datoBeregnet: LocalDate,
    val totalBelop: Int,
    val perioder: List<SimulertPeriodeDto>,
)

data class SimulertPeriodeDto(
    val fom: LocalDate,
    val tom: LocalDate,
    val utbetalinger: List<Utbetaling>,
)

data class UtbetalingDto(
    val fagområde: String,
    val fagSystemId: String,
    val utbetalesTilId: String,
    val forfall: LocalDate,
    val feilkonto: Boolean,
    val detaljer: List<PosteringDto>,
)

// Tilsvarer én rad i regnskapet
data class PosteringDto(
    val type: String,
    val faktiskFom: LocalDate,
    val faktiskTom: LocalDate,
    val belop: Int,
    val sats: Double,
    val satstype: String?,
    val klassekode: String,
    val trekkVedtakId: Long?,
    val refunderesOrgNr: String?,
)
