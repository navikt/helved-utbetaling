package utsjekk.iverksetting.utbetalingsoppdrag

import no.nav.utsjekk.kontrakter.oppdrag.Opphør
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsperiode
import utsjekk.iverksetting.AndelData
import utsjekk.iverksetting.Behandlingsinformasjon
import utsjekk.iverksetting.StønadsdataDagpenger
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Lager mal for generering av utbetalingsperioder med tilpasset setting av verdier basert på parametre
 *
 * @param[vedtak] for vedtakdato og opphørsdato hvis satt
 * @param[erEndringPåEksisterendePeriode] ved true vil oppdrag sette asksjonskode ENDR på linje og ikke referere bakover
 * @return mal med tilpasset lagPeriodeFraAndel
 */
internal data class UtbetalingsperiodeMal(
    val behandlingsinformasjon: Behandlingsinformasjon,
    val erEndringPåEksisterendePeriode: Boolean = false,
) {
    /**
     * Lager utbetalingsperioder som legges på utbetalingsoppdrag. En utbetalingsperiode tilsvarer linjer hos økonomi
     *
     * Denne metoden brukes også til simulering og på dette tidspunktet er ikke vedtaksdatoen satt.
     * Derfor defaulter vi til now() når vedtaksdato mangler.
     *
     * @param[andel] andel som skal mappes til periode
     * @param[periodeIdOffset] brukes til å synce våre linjer med det som ligger hos økonomi
     * @param[forrigePeriodeIdOffset] peker til forrige i kjeden. Kun relevant når IKKE erEndringPåEksisterendePeriode
     * @param[opphørKjedeFom] fom-dato fra tidligste periode i kjede med endring
     * @return Periode til utbetalingsoppdrag
     */
    fun lagPeriodeFraAndel(
        andel: AndelData,
        opphørKjedeFom: LocalDate? = null,
    ): Utbetalingsperiode =
        Utbetalingsperiode(
            erEndringPåEksisterendePeriode = erEndringPåEksisterendePeriode,
            opphør =
                if (erEndringPåEksisterendePeriode) {
                    val opphørDatoFom =
                        opphørKjedeFom
                            ?: error("Mangler opphørsdato for kjede")
                    Opphør(opphørDatoFom)
                } else {
                    null
                },
            forrigePeriodeId = andel.forrigePeriodeId,
            periodeId = andel.periodeId ?: error("Mangler periodeId på andel=${andel.id}"),
            vedtaksdato = behandlingsinformasjon.vedtaksdato,
            klassifisering = andel.stønadsdata.tilKlassifisering(),
            fom = andel.fom,
            tom = andel.tom,
            sats = BigDecimal(andel.beløp),
            satstype = andel.satstype,
            utbetalesTil = behandlingsinformasjon.personident,
            behandlingId = behandlingsinformasjon.behandlingId.id,
            fastsattDagsats =
                if (andel.stønadsdata is StønadsdataDagpenger) {
                    BigDecimal(andel.stønadsdata.fastsattDagsats.toInt())
                } else {
                    null
                },
        )
}
