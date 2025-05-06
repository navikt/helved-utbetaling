package utsjekk.iverksetting

import no.nav.utsjekk.kontrakter.felles.Satstype
import utsjekk.utbetaling.*

// Forslag til hvordan vi kan migrere iverksettinger til et utbetalingsformat som ligner på det vi har i dag.
// TilkjentYtelse er det utbetalingsgeneratoren regner ut. Den finner ut hva som skal kjedes og setter periodeIder
// på periodene.
//
// Det vi gjør her er å finne ut av periodeIdene til de eksisterende kjedene og sette de i `lastPeriodeId`-
// feltet til respektive kjeder.
//
// TODO:
//  [ ] Mappe fra (nytt) API-object til UtbetalingV2
//  [ ] Mappe fra UtbetalingV2 til oppdrag
//  [x] Mappe fra Iverksetting/TilkjentYtelse til UtbetalingV2
//  [ ] Teste at mappingen fungerer for samtlige iverksettinger i dev. Mappingen fungerer hvis oppdragene som genereres
//      med den nye mappingen er 100% lik oppdragene som genereres med Utbetalingsgeneratoren
//  [ ] Finne ut hvordan vi skal kjede. Er det på satstype + stønadstype + måned?
//  [ ] Migrere fra utbetaling v1 til v2?
//  [ ] Migrere data i iverksetting-tabellen og lagre i utbetaling-tabellen
//  [ ] Status-endepunktet til iverksetting henter status fra topic

object IverksettingMigrator {
    // Mapping fra gammelt iverksettingsformat til nytt utbetalingsformat
    // Brukes for migrering av det vi allerede har liggende i db
    fun migrate(iverksetting: Iverksetting, tilkjentYtelse: TilkjentYtelse): UtbetalingV2 {
        return UtbetalingV2(
            sakId = utsjekk.utbetaling.SakId(iverksetting.sakId.id),
            behandlingId = utsjekk.utbetaling.BehandlingId(iverksetting.behandlingId.id),
            personident = Personident(iverksetting.personident),
            vedtakstidspunkt = iverksetting.vedtak.vedtakstidspunkt,
            beslutterId = Navident(iverksetting.vedtak.beslutterId),
            saksbehandlerId = Navident(iverksetting.vedtak.saksbehandlerId),
            kjeder = lagKjeder(iverksetting, tilkjentYtelse),
            avvent = null
        )
    }

    private fun lagKjeder(
        iverksetting: Iverksetting,
        tilkjentYtelse: TilkjentYtelse
    ): Map<String, UtbetalingV2.Utbetalingsperioder> {
        return iverksetting.vedtak.tilkjentYtelse.andelerTilkjentYtelse.let { andeler ->
            andeler
                .groupBy { it.stønadsdata.tilKjedenøkkel() }
                .mapValues { (kjedenøkkel, andeler) ->
                    val stønadstype = kjedenøkkel.stønadstype()
                    val periodeId = requireNotNull(tilkjentYtelse.sisteAndelPerKjede[kjedenøkkel]?.periodeId) {
                        "Fant en andel uten siste periodeID"
                    }
                    UtbetalingV2.Utbetalingsperioder(
                        satstype = andeler.first().satstype.satstype(), // Alle andeler skal ha samme satstype. Må kanskje valideres?
                        stønad = stønadstype,
                        lastPeriodeId = "${iverksetting.sakId.id}#$periodeId",
                        perioder = andeler.map { andel: AndelTilkjentYtelse ->
                            UtbetalingV2.Utbetalingsperiode(
                                fom = andel.periode.fom,
                                tom = andel.periode.tom,
                                beløp = andel.beløp.toUInt(),
                                betalendeEnhet = andel.stønadsdata.betalendeEnhet(),
                                vedtakssats = andel.stønadsdata.vedtakssats(),
                            )
                        }
                    )
                }
                .mapKeys { (kjedenøkkel, _) -> kjedenøkkel.stønadstype().name }
        }
    }
}

private fun Kjedenøkkel.stønadstype(): Stønadstype {
    return Stønadstype.fraKode(this.klassifiseringskode)
}

private fun Stønadsdata.vedtakssats(): UInt? {
    return when (this) {
        is StønadsdataDagpenger -> this.fastsattDagsats
        is StønadsdataAAP -> this.fastsattDagsats
        else -> null
    }
}

private fun Stønadsdata.betalendeEnhet(): NavEnhet? {
    return when (this) {
        is StønadsdataTilleggsstønader -> this.brukersNavKontor?.let { NavEnhet(it.enhet) }
        is StønadsdataTiltakspenger -> NavEnhet(this.brukersNavKontor.enhet)
        else -> null
    }
}

private fun Satstype.satstype(): utsjekk.utbetaling.Satstype {
    return when (this) {
        Satstype.DAGLIG -> utsjekk.utbetaling.Satstype.VIRKEDAG
        Satstype.DAGLIG_INKL_HELG -> utsjekk.utbetaling.Satstype.DAG
        Satstype.MÅNEDLIG -> utsjekk.utbetaling.Satstype.MND
        Satstype.ENGANGS -> utsjekk.utbetaling.Satstype.ENGANGS
    }
}