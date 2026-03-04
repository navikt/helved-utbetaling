package abetal.consumers

import abetal.*
import models.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Parameterized test scenarios that can be run across all consumers.
 */
abstract class ConsumerParameterizedTestBase<TMessage>: ConsumerTestBase() {
    abstract val fagsystem: Fagsystem
    abstract val fagområde: String
    abstract val saksbehId: String
    abstract val periodetype: Periodetype
    abstract val defaultStønad: Stønadstype

    abstract fun createMessage(sakId: String, behandlingId: String, perioder: List<TestPeriode>): TMessage
    abstract fun produceMessage(transactionId: String, message: TMessage)
    abstract fun createUtbetalingId(sakId: String, uniqueKey: String, stønad: Stønadstype): UtbetalingId
    
    open val sakerFagsystem: Fagsystem get() = fagsystem
    open val expectedUtbetFrekvens: String = "MND"
    
    /**
     * Get all UtbetalingIds that should be created from the periods.
     * Default: one UtbetalingId per period
     * Override for AAP (one per meldekort) or Historisk (single ID for all periods)
     */
    open fun getExpectedUtbetalingIds(sakId: String, perioder: List<TestPeriode>): List<UtbetalingId> {
        return perioder.map { createUtbetalingId(sakId, it.uniqueKey, defaultStønad) }
    }
    
    /**
     * Common test scenario: Single period creates single utbetaling
     */
    @TestFactory
    open fun `single period creates single utbetaling and oppdrag`() = listOf(
        DynamicTest.dynamicTest("1 period creates utbetaling(er) with 1 oppdrag") {
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
            
            TestRuntime.topics.utbetalinger.assertThat().isEmpty()
            
            val oppdrag = tid.getOppdragWithBasics(
                kodeEndring = "NY",
                kodeFagomraade = fagområde,
                sakId = sid.id,
                saksbehId = saksbehId,
                expectedLines = perioder.size,
                utbetFrekvens = expectedUtbetFrekvens
            )
            
            // Check all UIDs are in pending-utbetalinger (chain assertions)
            var assertion = TestRuntime.topics.pendingUtbetalinger.assertThat()
            uids.forEach { uid ->
                assertion = assertion.has(uid.toString())
            }
            kvitterOk(tid, oppdrag, uids)
            assertUtbetalinger(uids)
            TestRuntime.topics.pendingUtbetalinger.assertThat().isEmpty()
            `assert empty topic`()
        }
        
    )
    
    /**
     * Common test scenario: Multiple periods
     */
    @TestFactory
    open fun `multiple periods create multiple utbetalinger`() = listOf(
        DynamicTest.dynamicTest("2 periods create utbetaling(er) with 1 oppdrag") {
            val sid = SakId("$nextInt")
            val bid = BehandlingId("$nextInt")
            val tid = UUID.randomUUID().toString()
            val perioder = listOf(
                TestPeriode(7.jun, 18.jun, beløp = 553u, sats = 1077u, uniqueKey = "period1"),
                TestPeriode(7.jul, 20.jul, beløp = 779u, sats = 2377u, uniqueKey = "period2")
            )
            val uids = getExpectedUtbetalingIds(sid.id, perioder)
            
            // Setup empty saker for first utbetaling on sak
            TestRuntime.topics.saker.produce(SakKey(sid, sakerFagsystem)) { emptySet<UtbetalingId>() }
            
            val message = createMessage(
                sakId = sid.id,
                behandlingId = bid.id,
                perioder = perioder
            )
            
            produceMessage(tid, message)
            
            tid.assertStatus(expectedFagsystem = fagsystem)
            
            TestRuntime.topics.utbetalinger.assertThat().isEmpty()
            
            val oppdrag = tid.getOppdragWithBasics(
                kodeEndring = "NY",
                kodeFagomraade = fagområde,
                sakId = sid.id,
                saksbehId = saksbehId,
                expectedLines = 2,
                utbetFrekvens = expectedUtbetFrekvens
            )
            
            // Check all UIDs are in pending-utbetalinger (chain assertions to avoid re-reading topic)
            var assertion = TestRuntime.topics.pendingUtbetalinger.assertThat()
            uids.forEach { uid ->
                assertion = assertion.has(uid.toString())
            }
            
            kvitterOk(tid, oppdrag, uids)
            assertUtbetalinger(uids)
            `assert empty topic`()
        }
    )
    
    /**
     * Common test scenario: Update existing utbetaling
     */
    @TestFactory
    open fun `update existing utbetaling`() = listOf(
        DynamicTest.dynamicTest("updating period amount creates ENDR oppdrag") {
            val sid = SakId("$nextInt")
            val bid1 = BehandlingId("$nextInt")
            val bid2 = BehandlingId("$nextInt")
            val perioder = listOf(TestPeriode(1.jun, 15.jun, 200u, 200u))
            val uids = getExpectedUtbetalingIds(sid.id, perioder)
            
            // Create initial utbetaling(er)
            uids.forEach { uid ->
                TestHelpers.createExistingUtbetaling(
                    uid = uid,
                    sakId = sid,
                    behandlingId = bid1,
                    fagsystem = fagsystem,
                    sakerFagsystem = sakerFagsystem,
                    stønad = defaultStønad,
                    periodetype = periodetype,
                    perioder = {
                        add(Utbetalingsperiode(1.jun, 15.jun, 100u))
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
                utbetFrekvens = expectedUtbetFrekvens
            )
            
            // Check all UIDs are in pending-utbetalinger (chain assertions)
            var assertion = TestRuntime.topics.pendingUtbetalinger.assertThat()
            uids.forEach { uid ->
                assertion = assertion.has(uid.toString())
            }
            kvitterOk(tid, oppdrag, uids)
            assertUtbetalinger(uids)
            `assert empty topic`()
        }
    )
    
    /**
     * Common test scenario: Delete/Opphør existing utbetaling
     * 
     * NOTE: This test is complex due to subtle differences between consumers:
     * - AAP/TP create one utbetaling per stønad type
     * - TS/DP create one utbetaling per period
     * - The idempotency logic requires careful setup
     * 
     * Consider implementing this test in individual consumer test classes
     * for better clarity and maintainability.
     */
    @TestFactory
    open fun `delete existing utbetaling creates opphør`() = emptyList<DynamicTest>()
    
    /**
     * Common test scenario: Empty utbetaling returns OK status
     * Should be overridden for consumers where this isn't valid behavior
     */
    @TestFactory
    open fun `empty utbetaling returns OK`() = listOf(
        DynamicTest.dynamicTest("utbetaling without perioder gives status OK") {
            val sid = SakId("$nextInt")
            val bid = BehandlingId("$nextInt")
            val tid = UUID.randomUUID().toString()
            
            val emptyMessage = createMessage(
                sakId = sid.id,
                behandlingId = bid.id,
                perioder = emptyList()
            )
            
            produceMessage(tid, emptyMessage)
            
            TestRuntime.topics.status.assertThat()
                .has(tid)
                .with(tid) { assertEquals(Status.OK, it.status) }
            `assert empty topic`()
        }
    )
    
    /**
     * Common test scenario: Simulation without changes returns OK
     * Tests that simulating the same utbetaling that already exists does not create a simulering request
     */
    @TestFactory
    open fun `simulering uten endring`() = listOf(
        DynamicTest.dynamicTest("simulation without changes returns OK") {
            val key = UUID.randomUUID().toString()
            val sid = SakId("$nextInt")
            val bid = BehandlingId("$nextInt")
            val vedtakstidspunkt = 14.jun.atStartOfDay()
            val periode = TestPeriode(
                fom = 1.jan,
                tom = 2.jan,
                beløp = 100u,
                sats = 100u,
                uniqueKey = "period1"
            )
            val uid = createUtbetalingId(sid.id, periode.uniqueKey, defaultStønad)
            
            // First create an existing utbetaling
            TestRuntime.topics.utbetalinger.produce(uid.toString()) {
                utbetaling(
                    action = Action.CREATE,
                    uid = uid,
                    sakId = sid,
                    behandlingId = bid,
                    originalKey = key,
                    stønad = defaultStønad,
                    personident = Personident("12345678910"),
                    vedtakstidspunkt = vedtakstidspunkt,
                    beslutterId = Navident(saksbehId),
                    saksbehandlerId = Navident(saksbehId),
                    fagsystem = fagsystem,
                    periodetype = periodetype
                ) {
                    periode(periode.fom, periode.tom, periode.beløp, periode.sats)
                }
            }
            
            // Setup saker topic so the system knows this utbetaling exists
            TestRuntime.topics.saker.produce(SakKey(sid, sakerFagsystem)) {
                setOf(uid)
            }
            
            // Now simulate the exact same utbetaling (dryrun=true) with same vedtakstidspunkt
            val dryrunMessage = createMessageDryrun(
                sakId = sid.id,
                behandlingId = bid.id,
                perioder = listOf(periode),
                vedtakstidspunkt = vedtakstidspunkt
            )
            
            produceMessage(key, dryrunMessage)
            
            TestRuntime.topics.status.assertThat()
                .has(key)
                .with(key) { statusReply ->
                    assertEquals(Status.OK, statusReply.status)
                }
            
            // Should not create a simulering since nothing changed
            TestRuntime.topics.simulering.assertThat().hasNot(key)
            `assert empty topic`()
        }
    )
    
    /**
     * Get the expected klassekode (e.g., "DAGPENGER", "AAPOR", "TPTPAFT", etc.)
     * Used in simulering assertions
     */
    abstract val expectedKlassekode: String
    
    /**
     * Common test scenario: Simulate (dryrun) new utbetaling creates simulering request
     * Tests that dryrun=true creates a simulering request without persisting anything
     */
    @TestFactory
    open fun `simuler utbetaling creates simulering request`() = listOf(
        DynamicTest.dynamicTest("dryrun creates simulering without persisting") {
            val sid = SakId("$nextInt")
            val bid = BehandlingId("$nextInt")
            val tid = UUID.randomUUID().toString()
            val periode = TestPeriode(
                fom = 7.jun,
                tom = 18.jun,
                beløp = 553u,
                sats = 1077u,
                uniqueKey = "period1"
            )
            
            // Create dryrun message
            val dryrunMessage = createMessageDryrun(
                sakId = sid.id,
                behandlingId = bid.id,
                perioder = listOf(periode),
                vedtakstidspunkt = LocalDateTime.now()
            )
            
            produceMessage(tid, dryrunMessage)
            
            // Should not create status, utbetaling, or oppdrag
            TestRuntime.topics.status.assertThat().isEmpty()
            TestRuntime.topics.utbetalinger.assertThat().isEmpty()
            TestRuntime.topics.oppdrag.assertThat().isEmpty()
            
            // Should create simulering request
            TestRuntime.topics.simulering.assertThat()
                .hasTotal(1)
                .has(tid)
                .with(tid) { simulering ->
                    assertEquals("12345678910", simulering.request.oppdrag.oppdragGjelderId)
                    assertEquals("NY", simulering.request.oppdrag.kodeEndring)
                    assertEquals(fagområde, simulering.request.oppdrag.kodeFagomraade)
                    assertEquals(sid.id, simulering.request.oppdrag.fagsystemId)
                    assertEquals(expectedUtbetFrekvens, simulering.request.oppdrag.utbetFrekvens)
                    assertEquals(saksbehId, simulering.request.oppdrag.saksbehId)
                    assertEquals(1, simulering.request.oppdrag.oppdragslinjes.size)
                    assertNull(simulering.request.oppdrag.oppdragslinjes[0].refDelytelseId)
                    simulering.request.oppdrag.oppdragslinjes[0].let {
                        assertEquals("NY", it.kodeEndringLinje)
                        assertEquals(expectedKlassekode, it.kodeKlassifik)
                        val expectedSats = when (periodetype){
                            Periodetype.EN_GANG -> periode.sats.toLong()
                            else -> periode.beløp.toLong()
                        }
                        assertEquals(expectedSats, it.sats.toLong())
                        assertEquals(it.datoVedtakFom, it.datoKlassifikFom)
                    }
                }
            `assert empty topic`()
        }
    )
    
    /**
     * Creates a dryrun (simulation) message.
     * Default implementation delegates to createMessage, but consumers can override
     * if they need special handling for dryrun messages.
     */
    open fun createMessageDryrun(
        sakId: String,
        behandlingId: String,
        perioder: List<TestPeriode>,
        vedtakstidspunkt: LocalDateTime
    ): TMessage {
        return createMessage(sakId, behandlingId, perioder)
    }
}

data class TestPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: UInt,
    val sats: UInt,
    val uniqueKey: String = "period1"  // Used for creating utbetalingId
)
