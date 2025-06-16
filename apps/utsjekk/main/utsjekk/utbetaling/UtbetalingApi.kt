package utsjekk.utbetaling

import utsjekk.avstemming.erHelg
import utsjekk.avstemming.nesteUkedag
import utsjekk.badRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class UtbetalingApi(
    val sakId: String,
    val behandlingId: String,
    val personident: String,
    val vedtakstidspunkt: LocalDateTime,
    val stønad: Stønadstype,
    val beslutterId: String,
    val saksbehandlerId: String,
    val periodeType: PeriodeType,
    val perioder: List<UtbetalingsperiodeApi>,
    val avvent: Avvent?,
    val erFørsteUtbetaling: Boolean? = null,
) {
    companion object {
        fun from(domain: Utbetaling) = UtbetalingApi(
            sakId = domain.sakId.id,
            behandlingId = domain.behandlingId.id,
            personident = domain.personident.ident,
            vedtakstidspunkt = domain.vedtakstidspunkt,
            stønad = domain.stønad,
            beslutterId = domain.beslutterId.ident,
            saksbehandlerId = domain.saksbehandlerId.ident,
            periodeType = PeriodeType.from(domain.satstype),
            perioder = UtbetalingsperiodeApi.from(domain.perioder, domain.satstype),
            avvent = domain.avvent,
        )
    }

    fun validate() {
        failOnEmptyPerioder()
        failOnÅrsskifte()
        failOnDuplicatePerioder()
        failOnTomBeforeFom()
        failOnIllegalUseOfFastsattDagsats()
        failOnInconsistentPeriodeType()
        failOnIllegalFutureUtbetaling()
        failOnTooLongPeriods()
        failOnZeroBeløp()
        failOnTooLongSakId()
        failOnTooLongBehandlingId()
        // validate stønadstype opp mot e.g. fastsattDagsats
    }
}

enum class PeriodeType {
    /** man - fre */
    UKEDAG,
    /** man - søn */
    DAG,
    /** hele måneder */
    MND,
    /** engangsutbetaling */
    EN_GANG;

    companion object {
        fun from(satstype: Satstype): PeriodeType = when (satstype) {
            Satstype.DAG -> PeriodeType.DAG
            Satstype.VIRKEDAG -> PeriodeType.UKEDAG
            Satstype.MND -> PeriodeType.MND
            Satstype.ENGANGS -> PeriodeType.EN_GANG
        }
    }
}

data class UtbetalingsperiodeApi(
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: UInt,

    /**
     * Dette feltet brukes hvis budsjettet til et lokalkontor skal brukes i beregningene.
     */
    val betalendeEnhet: String? = null,

    /**
     * Dagpenger og AAP har særegen skatteberegning og må fylle inn dette feltet.
     */
    val fastsattDagsats: UInt? = null,
) {
    companion object {
        fun from(domain: List<Utbetalingsperiode>, satstype: Satstype): List<UtbetalingsperiodeApi> = domain.map {
            when (satstype) {
                Satstype.ENGANGS, Satstype.MND -> listOf(
                    UtbetalingsperiodeApi(
                        fom = it.fom,
                        tom = it.tom,
                        beløp = it.beløp,
                        betalendeEnhet = it.betalendeEnhet?.enhet,
                        fastsattDagsats = it.fastsattDagsats,
                    )
                )

                Satstype.DAG, Satstype.VIRKEDAG -> buildList {
                    var date = it.fom
                    while (date.isBefore(it.tom) || date.isEqual(it.tom)) {
                        val periode = UtbetalingsperiodeApi(
                            fom = date,
                            tom = date,
                            beløp = it.beløp,
                            betalendeEnhet = it.betalendeEnhet?.enhet,
                            fastsattDagsats = it.fastsattDagsats,
                        )
                        add(periode)
                        date = if (satstype == Satstype.DAG) date.plusDays(1) else date.nesteUkedag()
                    }
                }
            }
        }.flatten()
    }
}

private fun UtbetalingApi.failOnEmptyPerioder() {
    if (perioder.isEmpty()) {
        badRequest(
            msg = "perioder kan ikke være tom",
            field = "perioder",
            doc = "opprett_en_utbetaling"
        )
    }
}

private fun UtbetalingApi.failOnÅrsskifte() {
    if (periodeType != PeriodeType.EN_GANG) return
    if (perioder.minBy { it.fom }.fom.year != perioder.maxBy { it.tom }.tom.year) {
        badRequest(
            msg = "periode strekker seg over årsskifte",
            field = "tom",
            doc = "opprett_en_utbetaling"
        )
    }
}

private fun UtbetalingApi.failOnDuplicatePerioder() {
    if (perioder.groupBy { it.fom }.any { (_, perioder) -> perioder.size != 1 }) {
        badRequest(
            msg = "kan ikke sende inn duplikate perioder",
            field = "fom",
            doc = "opprett_en_utbetaling"
        )
    }
    if (perioder.groupBy { it.tom }.any { (_, perioder) -> perioder.size != 1 }) {
        badRequest(
            msg = "kan ikke sende inn duplikate perioder",
            field = "tom",
            doc = "opprett_en_utbetaling"
        )
    }
}

private fun UtbetalingApi.failOnTomBeforeFom() {
    if (!perioder.all { it.fom <= it.tom }) {
        badRequest(
            msg = "fom må være før eller lik tom",
            field = "fom",
            doc = "opprett_en_utbetaling"
        )
    }
}

private fun UtbetalingApi.failOnIllegalUseOfFastsattDagsats() {
    when (stønad) {
        is StønadTypeDagpenger -> {}
        is StønadTypeAAP -> {}
        else -> {
            if (perioder.any { it.fastsattDagsats != null }) {
                badRequest(
                    msg = "reservert felt for Dagpenger og AAP",
                    field = "fastsattDagsats",
                    doc = "opprett_en_utbetaling"
                )
            }
        }
    }
}

private fun UtbetalingApi.failOnInconsistentPeriodeType() {
    val consistent = when (periodeType) {
        PeriodeType.UKEDAG -> perioder.all { it.fom == it.tom } && perioder.none { it.fom.erHelg() }
        PeriodeType.DAG -> perioder.all { it.fom == it.tom }
        PeriodeType.MND -> perioder.all { it.fom.dayOfMonth == 1 && it.tom.plusDays(1) == it.fom.plusMonths(1) }
        PeriodeType.EN_GANG -> perioder.all { it.fom.year == it.tom.year } // tillater engangs over årsskifte 
    }
    if (!consistent) {
        badRequest(
            msg = "inkonsistens blant datoene i periodene.",
            doc = "opprett_en_utbetaling"
        )
    }
}

private fun UtbetalingApi.failOnIllegalFutureUtbetaling() {
    if (periodeType in listOf(PeriodeType.DAG, PeriodeType.UKEDAG) && perioder.maxBy{ it.tom }.tom.isAfter(LocalDate.now())) {
        badRequest(
            msg = "fremtidige utbetalinger er ikke støttet for periode dag/ukedag.",
            field = "periode.tom",
            doc = "opprett_en_utbetaling"
        )
    }
}

private fun UtbetalingApi.failOnTooLongPeriods() {
    if (periodeType in listOf(PeriodeType.DAG, PeriodeType.UKEDAG)) {
        val min = perioder.minBy { it.fom }.fom
        val max = perioder.maxBy { it.tom }.tom
        if (ChronoUnit.DAYS.between(min, max)+1 >= 1000) {
            badRequest(
                msg = "$periodeType støtter maks periode på 1000 dager",
                field = "perioder",
                doc = "opprett_en_utbetaling"
            )
        }
    }
}

private fun UtbetalingApi.failOnZeroBeløp() {
    if (perioder.any { it.beløp == 0u }) {
        badRequest(
            msg = "beløp kan ikke være 0",
            field = "beløp",
            doc = "opprett_en_utbetaling"
        )
    }
}

private fun UtbetalingApi.failOnTooLongSakId() {
    if (sakId.length > 30) {
        badRequest(
            msg = "sakId kan være maks 30 tegn langt",
            field = "sakId",
            doc = "opprett_en_utbetaling"
        )
    }
}

private fun UtbetalingApi.failOnTooLongBehandlingId() {
    if (behandlingId.length > 30) {
        badRequest(
            msg = "behandlingId kan være maks 30 tegn langt",
            field = "behandlingId",
            doc = "opprett_en_utbetaling"
        )
    }
}
