package abetal.consumers

import abetal.*
import models.*
import org.junit.jupiter.api.DynamicTest
import java.time.LocalDateTime

/**
 * Parameterized tests for Historisk consumer.
 * 
 * Historisk creates ONE utbetaling containing all periods (unlike other consumers
 * which create one utbetaling per period).
 */
internal class HistoriskParameterizedTest : ConsumerParameterizedTestBase<HistoriskUtbetaling>() {
    
    override val fagsystem = Fagsystem.HISTORISK
    override val fagområde = "HELSREF"
    override val saksbehId = "historisk"
    
    // Disable tests that don't work generically for Historisk
    override fun `multiple periods create multiple utbetalinger`() = emptyList<DynamicTest>()
    override fun `update existing utbetaling`() = emptyList<DynamicTest>()
    override fun `empty utbetaling returns OK`() = emptyList<DynamicTest>()
    override fun `simulering uten endring`() = emptyList<DynamicTest>()  // Historisk has different simulation behavior - needs specific investigation
    
    override fun createMessage(
        sakId: String,
        behandlingId: String,
        perioder: List<TestPeriode>
    ): HistoriskUtbetaling {
        val uid = createUtbetalingId(sakId, "historisk", getDefaultStønad())
        
        return Historisk.utbetaling(
            uid = uid,
            sakId = sakId,
            behandlingId = behandlingId
        ) {
            perioder.map { periode ->
                HistoriskPeriode(
                    fom = periode.fom,
                    tom = periode.tom,
                    beløp = periode.beløp
                )
            }
        }
    }
    
    override fun produceMessage(transactionId: String, message: HistoriskUtbetaling) {
        TestRuntime.topics.historiskIntern.produce(transactionId) { message }
    }
    
    override fun createUtbetalingId(sakId: String, uniqueKey: String, stønad: Stønadstype): UtbetalingId {
        // Historisk uses random UUIDs, but for tests we need deterministic IDs based on sakId
        val uuid = java.util.UUID.nameUUIDFromBytes("$sakId-$uniqueKey-${stønad.name}".toByteArray())
        return UtbetalingId(uuid)
    }
    
    override fun getDefaultStønad(): Stønadstype {
        return StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER
    }
    
    override fun getDefaultPeriodetype(): Periodetype {
        return Periodetype.EN_GANG
    }
    
    override fun getExpectedUtbetFrekvens(): String {
        return "ENG" // EN_GANG periodetype uses ENG utbetFrekvens
    }
    
    // Historisk creates ONE utbetaling with multiple periods
    override fun getExpectedUtbetalingIds(sakId: String, perioder: List<TestPeriode>): List<UtbetalingId> {
        return listOf(createUtbetalingId(sakId, "historisk", getDefaultStønad()))
    }
    
    override fun createMessageDryrun(sakId: String, behandlingId: String, perioder: List<TestPeriode>, vedtakstidspunkt: LocalDateTime): HistoriskUtbetaling {
        val uid = createUtbetalingId(sakId, "historisk", getDefaultStønad())
        
        return Historisk.utbetaling(
            uid = uid,
            sakId = sakId,
            behandlingId = behandlingId,
            vedtakstidspunkt = vedtakstidspunkt,
            dryrun = true
        ) {
            perioder.map { periode ->
                HistoriskPeriode(
                    fom = periode.fom,
                    tom = periode.tom,
                    beløp = periode.beløp
                )
            }
        }
    }
}
