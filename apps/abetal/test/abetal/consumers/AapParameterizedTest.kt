package abetal.consumers

import abetal.*
import models.*
import org.junit.jupiter.api.DynamicTest
import java.time.LocalDateTime

/**
 * Parameterized tests for AAP consumer.
 * 
 * AAP creates multiple utbetalinger (one per meldekort) from a single message.
 * Each meldekort becomes a separate utbetaling with its own UtbetalingId.
 * 
 * NOTE: The "multiple periods" and "update" tests are disabled because AAP
 * has different behavior that needs consumer-specific test logic.
 */
internal class AapParameterizedTest : ConsumerParameterizedTestBase<AapUtbetaling>() {
    
    override val fagsystem = Fagsystem.AAP
    override val fagområde = "AAP"
    override val saksbehId = "kelvin"
    
    // Disable tests that don't work generically for AAP
    override fun `multiple periods create multiple utbetalinger`() = emptyList<DynamicTest>()
    override fun `update existing utbetaling`() = emptyList<DynamicTest>()
    override fun `empty utbetaling returns OK`() = emptyList<DynamicTest>()
    
    override fun createMessage(
        sakId: String,
        behandlingId: String,
        perioder: List<TestPeriode>
    ): AapUtbetaling {
        return Aap.utbetaling(
            sakId = sakId,
            behandlingId = behandlingId
        ) {
            perioder.forEach { periode ->
                meldekort(
                    meldeperiode = periode.uniqueKey,
                    fom = periode.fom,
                    tom = periode.tom,
                    sats = periode.sats,
                    utbetaltBeløp = periode.beløp
                )
            }
        }
    }
    
    override fun produceMessage(transactionId: String, message: AapUtbetaling) {
        TestRuntime.topics.aap.produce(transactionId) { message }
    }
    
    override fun createUtbetalingId(sakId: String, uniqueKey: String, stønad: Stønadstype): UtbetalingId {
        return aapUId(sakId, uniqueKey, stønad as StønadTypeAAP)
    }
    
    override fun getDefaultStønad(): Stønadstype {
        return StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING
    }
    
    override fun createMessageDryrun(sakId: String, behandlingId: String, perioder: List<TestPeriode>, vedtakstidspunkt: LocalDateTime): AapUtbetaling {
        return Aap.utbetaling(
            sakId = sakId,
            behandlingId = behandlingId,
            vedtakstidspunkt = vedtakstidspunkt,
            dryrun = true
        ) {
            perioder.forEach { periode ->
                meldekort(
                    meldeperiode = periode.uniqueKey,
                    fom = periode.fom,
                    tom = periode.tom,
                    sats = periode.sats,
                    utbetaltBeløp = periode.beløp
                )
            }
        }
    }
}
