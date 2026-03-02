package abetal.consumers

import abetal.*
import models.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import java.util.*

/**
 * Parameterized tests for Dagpenger consumer.
 * 
 * Uses testWithCleanup() wrapper to ensure cleanup after each test.
 * This provides the same safety checks as @AfterEach in regular tests.
 */
internal class DpParameterizedTest : ConsumerParameterizedTestBase<DpUtbetaling>() {
    
    override val fagsystem = Fagsystem.DAGPENGER
    override val fagområde = "DP"
    override val saksbehId = "dagpenger"
    
    // Disable multi-period test - DP has complex multi-utbetaling logic that needs specific test coverage in DpTest
    override fun `multiple periods create multiple utbetalinger`() = emptyList<DynamicTest>()
    
    override fun createMessage(
        sakId: String,
        behandlingId: String,
        perioder: List<TestPeriode>
    ): DpUtbetaling {
        val utbetalinger = perioder.flatMap { periode ->
            var current = periode.fom
            val result = mutableListOf<DpUtbetalingsdag>()
            while (!current.isAfter(periode.tom)) {
                if (!current.erHelg()) {  // Skip weekends!
                    result.add(
                        DpUtbetalingsdag(
                            meldeperiode = periode.uniqueKey,  // Use the uniqueKey from periode
                            dato = current,
                            sats = periode.sats,
                            utbetaltBeløp = periode.beløp,
                            utbetalingstype = Utbetalingstype.Dagpenger
                        )
                    )
                }
                current = current.plusDays(1)
            }
            result
        }
        
        return DpUtbetaling(
            sakId = sakId,
            behandlingId = behandlingId,
            ident = "12345678910",
            vedtakstidspunktet = java.time.LocalDateTime.now(),
            utbetalinger = utbetalinger,
            saksbehandler = saksbehId,
            beslutter = saksbehId
        )
    }
    
    override fun produceMessage(transactionId: String, message: DpUtbetaling) {
        TestRuntime.topics.dp.produce(transactionId) { message }
    }
    
    override fun createUtbetalingId(sakId: String, uniqueKey: String, stønad: Stønadstype): UtbetalingId {
        return dpUId(sakId, uniqueKey, stønad as StønadTypeDagpenger)
    }
    
    override fun getDefaultStønad(): Stønadstype {
        return StønadTypeDagpenger.DAGPENGER
    }
}
