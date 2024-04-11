package simulering

import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaa
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaaDetaljer
import no.nav.system.os.entiteter.beregningskjema.BeregningsPeriode
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse
import java.time.LocalDate

data class Simulering(
    val gjelderId: String,
    val gjelderNavn: String,
    val datoBeregnet: LocalDate,
    val totalBelop: Int,
    val perioder: List<SimulertPeriode>,
)

data class SimulertPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
    val utbetalinger: List<Utbetaling>,
)

data class Utbetaling(
    val fagSystemId: String,
    val utbetalesTilId: String,
    val utbetalesTilNavn: String,
    val forfall: LocalDate,
    val feilkonto: Boolean,
    val detaljer: List<Detaljer>,
)

data class Detaljer(
    val faktiskFom: LocalDate,
    val faktiskTom: LocalDate,
    val konto: String,
    val belop: Int,
    val tilbakeforing: Boolean,
    val sats: Double,
    val typeSats: String,
    val antallSats: Int,
    val uforegrad: Int,
    val utbetalingsType: String,
    val klassekode: String,
    val klassekodeBeskrivelse: String?,
    val refunderesOrgNr: String?,
)

fun SimulerBeregningResponse?.tilSimulering() =
    this?.response?.simulering?.let { simulering ->
        Simulering(
            gjelderId = simulering.gjelderId,
            gjelderNavn = simulering.gjelderNavn.trim(),
            datoBeregnet = LocalDate.parse(simulering.datoBeregnet),
            totalBelop = simulering.belop.intValueExact(),
            perioder = simulering.beregningsPeriode.map(BeregningsPeriode::tilSimulertPeriode),
        )
    }

private fun BeregningsPeriode.tilSimulertPeriode() =
    SimulertPeriode(
        fom = LocalDate.parse(periodeFom),
        tom = LocalDate.parse(periodeTom),
        utbetalinger = beregningStoppnivaa.map(BeregningStoppnivaa::tilUtbetaling),
    )

private fun BeregningStoppnivaa.tilUtbetaling() =
    Utbetaling(
        fagSystemId = fagsystemId.trim(),
        utbetalesTilId = utbetalesTilId.removePrefix("00"),
        utbetalesTilNavn = utbetalesTilNavn.trim(),
        forfall = LocalDate.parse(forfall),
        feilkonto = isFeilkonto,
        detaljer = beregningStoppnivaaDetaljer.map(BeregningStoppnivaaDetaljer::tilDetaljer),
    )

private fun BeregningStoppnivaaDetaljer.tilDetaljer() =
    Detaljer(
        faktiskFom = LocalDate.parse(faktiskFom),
        faktiskTom = LocalDate.parse(faktiskTom),
        konto = kontoStreng.trim(),
        belop = belop.intValueExact(),
        tilbakeforing = isTilbakeforing,
        sats = sats.toDouble(),
        typeSats = typeSats.trim(),
        antallSats = antallSats.intValueExact(),
        uforegrad = uforeGrad.intValueExact(),
        utbetalingsType = typeKlasse,
        klassekode = klassekode.trim(),
        klassekodeBeskrivelse = klasseKodeBeskrivelse.trim(),
        refunderesOrgNr = refunderesOrgNr?.removePrefix("00")?.trim(),
    )
