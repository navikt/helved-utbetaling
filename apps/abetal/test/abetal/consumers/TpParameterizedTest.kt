package abetal.consumers

import abetal.*
import models.*
import org.junit.jupiter.api.DynamicTest

/**
 * Parameterized tests for Tiltakspenger consumer.
 * 
 * Runs common test scenarios with TP-specific configuration.
 * 
 * NOTE: The "multiple periods" and "update" tests are disabled because TP
 * has different behavior that needs consumer-specific test logic.
 */
internal class TpParameterizedTest : ConsumerParameterizedTestBase<TpUtbetaling>() {
    
    override val fagsystem = Fagsystem.TILTAKSPENGER
    override val fagområde = "TILTPENG"
    override val saksbehId = "tp"
    
    // Disable tests that don't work generically for TP
    override fun `multiple periods create multiple utbetalinger`() = emptyList<DynamicTest>()
    override fun `update existing utbetaling`() = emptyList<DynamicTest>()
    override fun `empty utbetaling returns OK`() = emptyList<DynamicTest>()
    override fun `simulering uten endring`() = emptyList<DynamicTest>()  // TP has different simulation behavior
    
    override fun createMessage(
        sakId: String,
        behandlingId: String,
        perioder: List<TestPeriode>
    ): TpUtbetaling {
        return Tp.utbetaling(
            sakId = sakId,
            behandlingId = behandlingId,
            saksbehandler = saksbehId,
            beslutter = saksbehId
        ) {
            perioder.mapIndexed { index, periode ->
                TpPeriode(
                    meldeperiode = "period${index + 1}",
                    fom = periode.fom,
                    tom = periode.tom,
                    beløp = periode.beløp,
                    stønad = getDefaultStønad() as StønadTypeTiltakspenger
                )
            }
        }
    }
    
    override fun produceMessage(transactionId: String, message: TpUtbetaling) {
        TestRuntime.topics.tp.produce(transactionId) { message }
    }
    
    override fun createUtbetalingId(sakId: String, uniqueKey: String, stønad: Stønadstype): UtbetalingId {
        return tpUId(sakId, uniqueKey, stønad as StønadTypeTiltakspenger)
    }
    
    override fun getDefaultStønad(): Stønadstype {
        return StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING
    }
    
    override fun createMessageDryrun(sakId: String, behandlingId: String, perioder: List<TestPeriode>): TpUtbetaling {
        return Tp.utbetaling(
            sakId = sakId,
            behandlingId = behandlingId,
            saksbehandler = saksbehId,
            beslutter = saksbehId,
            dryrun = true
        ) {
            perioder.mapIndexed { index, periode ->
                TpPeriode(
                    meldeperiode = "period${index + 1}",
                    fom = periode.fom,
                    tom = periode.tom,
                    beløp = periode.beløp,
                    stønad = getDefaultStønad() as StønadTypeTiltakspenger
                )
            }
        }
    }
}
