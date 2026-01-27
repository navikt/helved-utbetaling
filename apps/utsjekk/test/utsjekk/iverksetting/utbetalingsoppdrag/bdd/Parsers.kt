package utsjekk.iverksetting.utbetalingsoppdrag.bdd

import models.kontrakter.Satstype
import models.kontrakter.StønadType
import models.kontrakter.StønadTypeDagpenger
import org.junit.jupiter.api.Assertions.assertTrue
import utsjekk.iverksetting.BehandlingId
import java.time.LocalDate

data class ForventetUtbetalingsoppdrag(
    override val behandlingId: BehandlingId,
    val erFørsteUtbetalingPåSak: Boolean,
    val utbetalingsperiode: List<ForventetUtbetalingsperiode>,
) : WithBehandlingId(behandlingId) {
    companion object {
        fun from(rows: List<UtbetalingsgeneratorBddTest.Expected>): ForventetUtbetalingsoppdrag {
            assertTrue(rows.all { row -> row.behandlingId == rows.first().behandlingId })
            assertTrue(rows.all { row -> row.førsteUtbetSak == rows.first().førsteUtbetSak })

            return ForventetUtbetalingsoppdrag(
                behandlingId = rows.first().behandlingId,
                erFørsteUtbetalingPåSak = rows.first().førsteUtbetSak,
                utbetalingsperiode = rows.map(ForventetUtbetalingsperiode::from)
            )
        }

        fun fromExpectedMedYtelse(rows: List<UtbetalingsgeneratorBddTest.ExpectedMedYtelse>): ForventetUtbetalingsoppdrag {
            assertTrue(rows.all { row -> row.behandlingId == rows.first().behandlingId })
            assertTrue(rows.all { row -> row.førsteUtbetSak == rows.first().førsteUtbetSak })

            return ForventetUtbetalingsoppdrag(
                behandlingId = rows.first().behandlingId,
                erFørsteUtbetalingPåSak = rows.first().førsteUtbetSak,
                utbetalingsperiode = rows.map(ForventetUtbetalingsperiode::fromExpectedMedYtelse)
            )
        }
    }
}

data class ForventetUtbetalingsperiode(
    val erEndringPåEksisterendePeriode: Boolean,
    val periodeId: Long,
    val forrigePeriodeId: Long?,
    val sats: Int,
    val ytelse: StønadType,
    val fom: LocalDate,
    val tom: LocalDate,
    val opphør: LocalDate?,
    val satstype: Satstype,
) {
    companion object {
        fun from(expected: UtbetalingsgeneratorBddTest.Expected) = ForventetUtbetalingsperiode(
            erEndringPåEksisterendePeriode = expected.erEndring,
            periodeId = expected.periodeId,
            forrigePeriodeId = expected.forrigePeriodeId,
            sats = expected.beløp,
            ytelse = StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR, // fixme: legg til i expected?
            fom = expected.fom,
            tom = expected.tom,
            opphør = expected.opphørsdato,
            satstype = expected.satstype,
        )
        fun fromExpectedMedYtelse(expected: UtbetalingsgeneratorBddTest.ExpectedMedYtelse) = ForventetUtbetalingsperiode(
            erEndringPåEksisterendePeriode = expected.erEndring,
            periodeId = expected.periodeId,
            forrigePeriodeId = expected.forrigePeriodeId,
            sats = expected.beløp,
            ytelse = expected.ytelse,
            fom = expected.fom,
            tom = expected.tom,
            opphør = expected.opphørsdato,
            satstype = expected.satstype,
        )
    }
}
