package utsjekk.simulering

import models.notImplemented
import utsjekk.utbetaling.FagsystemDto
import utsjekk.utbetaling.Stønadstype
import java.time.LocalDate
import kotlin.math.abs

data class SimuleringApi(
    val perioder: List<Simuleringsperiode>
) {
    companion object {
        fun from (res: client.SimuleringResponse) = SimuleringApi(
            perioder = res.perioder.map(Simuleringsperiode::from)
        )
    }
}

data class Simuleringsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val utbetalinger: List<SimulertUtbetaling>
) {
    companion object {
        fun from(dto: client.SimulertPeriode) = Simuleringsperiode(
            fom = dto.fom,
            tom = dto.tom,
            utbetalinger = dto.utbetalinger.map(SimulertUtbetaling::from)
        )
    }
}

data class SimulertUtbetaling(
    val fagsystem: FagsystemDto,
    val sakId: String,
    val utbetalesTil: String,
    val stønadstype: Stønadstype,
    val tidligereUtbetalt: Int,
    val nyttBeløp: Int,
) {
    companion object {
        fun from(dto: client.Utbetaling) = SimulertUtbetaling(
            fagsystem = FagsystemDto.entries.single { it.kode == dto.fagområde.name },
            sakId = dto.fagSystemId,
            utbetalesTil = dto.utbetalesTilId,
            stønadstype = dto.stønadstype,
            tidligereUtbetalt = dto.utbetalt(),
            nyttBeløp = dto.nyttBeløp(),
        )
    }
}

private fun client.Utbetaling.nyttBeløp() =
    detaljer.ytelser.sum - detaljer.feilutbetalinger.positive.sum

private val client.Utbetaling.stønadstype: Stønadstype
    get() {
        val raw = detaljer.first { it.type == client.PosteringType.YTEL }.klassekode
        return runCatching { Stønadstype.fraKode(raw) }.getOrNull()
            ?: notImplemented("Ikke implementert stønadstype for klassekode $raw")
    }

private val List<client.PosteringDto>.positive: List<client.PosteringDto>
    get() = filter { it.belop > 0 }

private val List<client.PosteringDto>.negative: List<client.PosteringDto>
    get() = filter { it.belop < 0 }

private val List<client.PosteringDto>.ytelser: List<client.PosteringDto>
    get() = filter { it.type == client.PosteringType.YTEL }

private val List<client.PosteringDto>.feilutbetalinger: List<client.PosteringDto>
    get() = filter { it.type == client.PosteringType.FEIL && it.klassekode == "KL_KODE_FEIL_ARBYT" }

private val List<client.PosteringDto>.justeringer: List<client.PosteringDto>
    get() = filter { it.type == client.PosteringType.FEIL && it.klassekode == "KL_KODE_JUST_ARBYT" }

private fun client.Utbetaling.utbetalt() =
    abs(detaljer.filter { it.type == client.PosteringType.YTEL && it.belop < 0 }.sum)

private val List<client.PosteringDto>.sum: Int
    get() = sumOf { it.belop }
