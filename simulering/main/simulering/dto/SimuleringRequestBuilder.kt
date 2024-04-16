package simulering.dto

import no.nav.system.os.entiteter.oppdragskjema.Attestant
import no.nav.system.os.entiteter.oppdragskjema.Enhet
import no.nav.system.os.entiteter.oppdragskjema.Grad
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg
import no.nav.system.os.entiteter.typer.simpletypes.KodeStatusLinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdrag
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal class SimuleringRequestBuilder(private val request: SimuleringRequestBody) {
    private companion object {
        private val tidsstempel = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    private val oppdrag =
        Oppdrag().apply {
            kodeFagomraade = request.fagområde
            kodeEndring = request.endringskode.verdi
            utbetFrekvens = request.utbetalingsfrekvens.verdi
            fagsystemId = request.fagsystemId
            oppdragGjelderId = request.personident.verdi
            saksbehId = request.saksbehandler
            datoOppdragGjelderFom = LocalDate.EPOCH.format(tidsstempel)
            enhet.add(
                Enhet().apply {
                    enhet = "8020"
                    typeEnhet = "BOS"
                    datoEnhetFom = LocalDate.EPOCH.format(tidsstempel)
                },
            )
        }

    fun build(): SimulerBeregningRequest {
        request.utbetalingslinjer.forEach {
            oppdrag.oppdragslinje.add(
                nyLinje(it).apply {
                    utbetalesTilId = request.mottaker.verdi
                },
            )
        }
        return SimulerBeregningRequest().apply {
            request =
                no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest()
                    .apply {
                        oppdrag = this@SimuleringRequestBuilder.oppdrag
                        simuleringsPeriode =
                            no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest.SimuleringsPeriode()
                                .apply {
                                    datoSimulerFom = førsteUtbetalingsdag.format(tidsstempel)
                                    datoSimulerTom = sisteUtbetalingsdag.format(tidsstempel)
                                }
                    }
        }
    }

    private val førsteUtbetalingsdag
        get() =
            checkNotNull(request.utbetalingslinjer.minByOrNull { it.fom }?.fom) {
                "Mangler utbetalingsdager"
            }

    private val sisteUtbetalingsdag
        get() =
            checkNotNull(request.utbetalingslinjer.maxByOrNull { it.tom }?.tom) {
                "Mangler utbetalingsdager"
            }

    private fun nyLinje(utbetalingslinje: Utbetalingslinje) =
        Oppdragslinje().apply {
            delytelseId = utbetalingslinje.delytelseId
            refDelytelseId = utbetalingslinje.refDelytelseId
            refFagsystemId = utbetalingslinje.refFagsystemId
            kodeEndringLinje = utbetalingslinje.endringskode.verdi
            kodeKlassifik = utbetalingslinje.klassekode
            kodeStatusLinje = utbetalingslinje.statuskode?.let { KodeStatusLinje.valueOf(it) }
            datoStatusFom = utbetalingslinje.datoStatusFom?.format(tidsstempel)
            datoVedtakFom = utbetalingslinje.fom.format(tidsstempel)
            datoVedtakTom = utbetalingslinje.tom.format(tidsstempel)
            sats = utbetalingslinje.sats.toBigDecimal()
            fradragTillegg = FradragTillegg.T
            typeSats = utbetalingslinje.satstype.verdi
            saksbehId = request.saksbehandler
            brukKjoreplan = "N"
            grad.add(
                Grad().apply {
                    typeGrad = "UFOR"
                    grad = utbetalingslinje.grad?.toBigInteger()
                },
            )
            attestant.add(
                Attestant().apply {
                    attestantId = request.saksbehandler
                },
            )
        }
}