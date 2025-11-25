package utsjekk.utbetaling.simulering

import models.notImplemented
import utsjekk.simulering.client
import utsjekk.utbetaling.FagsystemDto
import utsjekk.utbetaling.Stønadstype
import kotlin.math.abs

object SimuleringMapper {
    fun oppsummering(response: client.SimuleringResponse) = SimuleringApi(
        perioder = response.perioder.map { periode ->
            Simuleringsperiode(
                fom = periode.fom,
                tom = periode.tom,
                utbetalinger = periode.utbetalinger.map { utbetaling ->
                    SimulertUtbetaling(
                        fagsystem = FagsystemDto.entries.single { it.kode == utbetaling.fagområde.name },
                        sakId = utbetaling.fagSystemId,
                        utbetalesTil = utbetaling.utbetalesTilId,
                        stønadstype = utbetaling.stønadstype,
                        tidligereUtbetalt = utbetaling.utbetalt(),
                        nyttBeløp = utbetaling.nyttBeløp(),
                    )
                }
            )
        }
    )

    private fun client.Utbetaling.utbetalt() =
        abs(detaljer.filter { it.type == client.PosteringType.YTEL && it.belop < 0 }.sum)

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

    private val List<client.PosteringDto>.sum: Int
        get() = sumOf { it.belop }
}

