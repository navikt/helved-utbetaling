package abetal.consumers

import abetal.*
import models.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.time.LocalDate
import java.util.UUID

/**
 * Parameterized test scenarios that can be run across all consumers.
 * 
 * This allows testing common functionality (create, update, delete, etc.)
 * for all consumer types without duplicating test code.
 */
abstract class ConsumerParameterizedTestBase<TMessage>: ConsumerTestBase() {
    
    /**
     * Wrapper that ensures cleanup after each dynamic test.
     * This replicates the @AfterEach behavior from ConsumerTestBase.
     */
    private fun testWithCleanup(name: String, test: () -> Unit) = 
        DynamicTest.dynamicTest(name) {
            test()
            `assert empty topic`()
        }
    
    /**
     * Consumer-specific configuration
     */
    abstract val fagsystem: Fagsystem
    abstract val fagområde: String
    abstract val saksbehId: String
    abstract fun createMessage(sakId: String, behandlingId: String, perioder: List<TestPeriode>): TMessage
    abstract fun produceMessage(transactionId: String, message: TMessage)
    abstract fun createUtbetalingId(sakId: String, uniqueKey: String, stønad: Stønadstype): UtbetalingId
    abstract fun getDefaultStønad(): Stønadstype
    
    /**
     * Returns the fagsystem used for saker topic key.
     * Default is same as fagsystem, but TS overrides this to use TILLEGGSSTØNADER.
     */
    open fun getSakerFagsystem(): Fagsystem = fagsystem
    open fun getDefaultPeriodetype(): Periodetype = Periodetype.UKEDAG
    
    /**
     * Get the expected utbetFrekvens for oppdrag assertions.
     * Defaults to "MND", override for other periodetype (e.g., "ENG" for EN_GANG)
     */
    open fun getExpectedUtbetFrekvens(): String = "MND"
    
    /**
     * Determines how many utbetalinger are created per message.
     * Default: one utbetaling per period (DP, TS, TP)
     * AAP: one utbetaling per meldekort
     * Historisk: one utbetaling containing all periods
     */
    open fun getExpectedUtbetalingCount(periodeCount: Int): Int = periodeCount
    
    /**
     * Get all UtbetalingIds that should be created from the periods.
     * Default: one UtbetalingId per period
     * Override for AAP (one per meldekort) or Historisk (single ID for all periods)
     */
    open fun getExpectedUtbetalingIds(sakId: String, perioder: List<TestPeriode>): List<UtbetalingId> {
        return perioder.map { createUtbetalingId(sakId, it.uniqueKey, getDefaultStønad()) }
    }
    
    /**
     * Common test scenario: Single period creates single utbetaling
     */
    @TestFactory
    open fun `single period creates single utbetaling and oppdrag`() = listOf(
        testWithCleanup("1 period creates utbetaling(er) with 1 oppdrag") {
            val sid = SakId("$nextInt")
            val bid = BehandlingId("$nextInt")
            val tid = UUID.randomUUID().toString()
            val perioder = listOf(
                TestPeriode(
                    fom = 7.jun,
                    tom = 18.jun,
                    beløp = 553u,
                    sats = 1077u
                )
            )
            val uids = getExpectedUtbetalingIds(sid.id, perioder)
            
            val message = createMessage(
                sakId = sid.id,
                behandlingId = bid.id,
                perioder = perioder
            )
            
            produceMessage(tid, message)
            
            tid.assertStatus(expectedFagsystem = fagsystem)
            
            assertUtbetalingerEmpty()
            
            val oppdrag = tid.getOppdragWithBasics(
                kodeEndring = "NY",
                kodeFagomraade = fagområde,
                sakId = sid.id,
                saksbehId = saksbehId,
                expectedLines = perioder.size,
                utbetFrekvens = getExpectedUtbetFrekvens()
            )
            
            // Check all UIDs are in pending-utbetalinger (chain assertions)
            var assertion = TestRuntime.topics.pendingUtbetalinger.assertThat()
            uids.forEach { uid ->
                assertion = assertion.has(uid.toString())
            }
            tid.acknowledgeOppdrag(oppdrag, uids)
            assertUtbetalinger(uids)
            TestRuntime.topics.pendingUtbetalinger.assertThat().isEmpty()
        }
    )
    
    /**
     * Common test scenario: Multiple periods
     */
    @TestFactory
    open fun `multiple periods create multiple utbetalinger`() = listOf(
        testWithCleanup("2 periods create utbetaling(er) with 1 oppdrag") {
            val sid = SakId("$nextInt")
            val bid = BehandlingId("$nextInt")
            val tid = UUID.randomUUID().toString()
            val perioder = listOf(
                TestPeriode(7.jun, 18.jun, beløp = 553u, sats = 1077u, uniqueKey = "period1"),
                TestPeriode(7.jul, 20.jul, beløp = 779u, sats = 2377u, uniqueKey = "period2")
            )
            val uids = getExpectedUtbetalingIds(sid.id, perioder)
            
            // Setup empty saker for first utbetaling on sak
            TestRuntime.topics.saker.produce(SakKey(sid, getSakerFagsystem())) { emptySet<UtbetalingId>() }
            
            val message = createMessage(
                sakId = sid.id,
                behandlingId = bid.id,
                perioder = perioder
            )
            
            produceMessage(tid, message)
            
            tid.assertStatus(expectedFagsystem = fagsystem)
            
            assertUtbetalingerEmpty()
            
            val oppdrag = tid.getOppdragWithBasics(
                kodeEndring = "NY",
                kodeFagomraade = fagområde,
                sakId = sid.id,
                saksbehId = saksbehId,
                expectedLines = 2,
                utbetFrekvens = getExpectedUtbetFrekvens()
            )
            
            // Check all UIDs are in pending-utbetalinger (chain assertions to avoid re-reading topic)
            var assertion = TestRuntime.topics.pendingUtbetalinger.assertThat()
            uids.forEach { uid ->
                assertion = assertion.has(uid.toString())
            }
            
            tid.acknowledgeOppdrag(oppdrag, uids)
            assertUtbetalinger(uids)
            TestRuntime.topics.pendingUtbetalinger.assertThat().isEmpty()
        }
    )
    
    /**
     * Common test scenario: Update existing utbetaling
     */
    @TestFactory
    open fun `update existing utbetaling`() = listOf(
        testWithCleanup("updating period amount creates ENDR oppdrag") {
            val sid = SakId("$nextInt")
            val bid1 = BehandlingId("$nextInt")
            val bid2 = BehandlingId("$nextInt")
            val perioder = listOf(TestPeriode(1.jun, 15.jun, 200u, 200u))
            val uids = getExpectedUtbetalingIds(sid.id, perioder)
            
            // Create initial utbetaling(er)
            uids.forEach { uid ->
                TestScenarios.createExistingUtbetaling(
                    uid = uid,
                    sakId = sid,
                    behandlingId = bid1,
                    fagsystem = fagsystem,
                    sakerFagsystem = getSakerFagsystem(),
                    stønad = getDefaultStønad(),
                    periodetype = getDefaultPeriodetype(),
                    perioder = {
                        listOf(Utbetalingsperiode(1.jun, 15.jun, 100u))
                    }
                )
            }
            
            // Send update with different amount
            val tid = UUID.randomUUID().toString()
            val updateMessage = createMessage(
                sakId = sid.id,
                behandlingId = bid2.id,
                perioder = perioder
            )
            
            produceMessage(tid, updateMessage)
            
            tid.assertStatus(expectedFagsystem = fagsystem)
            
            val oppdrag = tid.getOppdragWithBasics(
                kodeEndring = "ENDR",
                kodeFagomraade = fagområde,
                sakId = sid.id,
                saksbehId = saksbehId,
                expectedLines = 2, // ENDR creates 2 lines: old with 0, new with amount
                utbetFrekvens = getExpectedUtbetFrekvens()
            )
            
            // Check all UIDs are in pending-utbetalinger (chain assertions)
            var assertion = TestRuntime.topics.pendingUtbetalinger.assertThat()
            uids.forEach { uid ->
                assertion = assertion.has(uid.toString())
            }
            tid.acknowledgeOppdrag(oppdrag, uids)
            assertUtbetalinger(uids)
            TestRuntime.topics.pendingUtbetalinger.assertThat().isEmpty()
        }
    )
}

data class TestPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: UInt,
    val sats: UInt,
    val uniqueKey: String = "period1"  // Used for creating utbetalingId
)
