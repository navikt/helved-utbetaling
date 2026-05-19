package urskog

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.jdbc.concurrency.transaction
import models.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import urskog.oppdrag.TestData
import urskog.oppdrag.nov
import urskog.oppdrag.seq
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Read-side barrier test: verifies Status.OK is never produced before the gating
 * conditions hold. Production correlates kvittering to the persisted oppdrag row
 * strictly via XML hash; mutating mmel onto the same instance changes the hash, so
 * in this test path the kvittering cannot resolve its row and the retry budget is
 * exhausted into Status.FEILET. The test pins the invariant that matters for the
 * barrier: Status.OK MUST NOT appear on the status topic when sakerAck is false.
 *
 * Steps:
 *   1. Oppdrag arrives first (with uids header) -> DaoOppdrag row inserted, sakerAck=false
 *   2. Pending utbetaling arrives -> pendingIsReady=true, oppdrag sent to MQ -> Status.HOS_OPPDRAG
 *   3. Kvittering (with mmel) arrives -> barrier does not resolve oppdrag row
 *      -> routed to Topics.retryKvittering -> exhausted to Status.FEILET (NEVER Status.OK)
 *   4. Utbetaling aggregated through saker pipeline -> markSakerAck fires -> sakerAck=true
 */
class StatusBarrierTest {

    @AfterEach
    fun cleanup() {
        TestRuntime.mq.reset()
        TestRuntime.topics.status.assertThat()
        TestRuntime.topics.oppdrag.assertThat()
        TestRuntime.topics.pendingUtbetalinger.assertThat()
        TestRuntime.topics.retryKvittering.assertThat()
        TestRuntime.topics.utbetalinger.assertThat()
        TestRuntime.topics.saker.assertThat()
    }

    @Test
    fun `status OK gated until both pendingIsReady and sakerAck are true`() {
        val transaction = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val sakIdStr = "$seq"
        val sakId = SakId(sakIdStr)
        val bid = "$seq"

        val oppdrag = TestData.oppdrag(
            fagsystemId = sakIdStr,
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = bid,
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "AAPOR",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1000L,
                )
            ),
        )

        TestRuntime.topics.oppdrag.produce(transaction, mapOf("uids" to uid.toString())) {
            oppdrag
        }
        assertEquals(0, TestRuntime.mq.sentOppdrag().size)

        val hashKey = DaoOppdrag.hash(oppdrag)
        TestRuntime.topics.pendingUtbetalinger.produce(uid.toString(), mapOf("hash_key" to hashKey)) {
            TestData.utbetaling(uid = uid, sakId = sakId, originalKey = transaction, fagsystem = Fagsystem.AAP)
        }
        TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
            .has(transaction, value = StatusReply(Status.HOS_OPPDRAG, Detaljer(ytelse = Fagsystem.AAP, linjer = listOf(
                DetaljerLinje(bid, 1.nov, 14.nov, null, 1000u, "AAPOR"),
            ))))
        assertEquals(1, TestRuntime.mq.sentOppdrag().size)

        val daoBefore = runBlocking {
            withContext(TestRuntime.context) {
                transaction { DaoOppdrag.findWithLockOrLegacy(hashKey, oppdrag) }
            }
        }
        assertNotNull(daoBefore)
        assertEquals(false, daoBefore.sakerAck, "sakerAck must still be false BEFORE saker aggregate catches up")

        oppdrag.mmel = TestData.ok()
        TestRuntime.topics.oppdrag.produce(transaction, mapOf("maxRetries" to "2")) { oppdrag }

        val statusAfterKvittering = TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
        statusAfterKvittering.with(transaction) { reply ->
            assertNotEquals(Status.OK, reply.status, "Barrier must not leak Status.OK while sakerAck is false")
        }

        TestRuntime.topics.retryKvittering.assertThat()

        TestRuntime.topics.utbetalinger.produce(uid.toString()) {
            TestData.utbetaling(uid = uid, sakId = sakId, originalKey = transaction, fagsystem = Fagsystem.AAP)
        }
        TestRuntime.topics.saker.assertThat()

        val daoAfter = runBlocking {
            withContext(TestRuntime.context) {
                transaction { DaoOppdrag.findWithLockOrLegacy(hashKey, oppdrag) }
            }
        }
        assertNotNull(daoAfter)
        assertEquals(true, daoAfter.sakerAck, "sakerAck must flip to true after saker aggregate catches up")
    }

    @Test
    fun `barrier never leaks Status OK when only pendingIsReady but sakerAck stays false`() {
        val transaction = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val sakIdStr = "$seq"
        val sakId = SakId(sakIdStr)
        val bid = "$seq"

        val oppdrag = TestData.oppdrag(
            fagsystemId = sakIdStr,
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = bid,
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "AAPOR",
                    datoVedtakFom = 1.nov,
                    datoVedtakTom = 14.nov,
                    typeSats = "DAG",
                    sats = 1000L,
                )
            ),
        )

        TestRuntime.topics.oppdrag.produce(transaction, mapOf("uids" to uid.toString())) { oppdrag }

        val hashKey = DaoOppdrag.hash(oppdrag)
        TestRuntime.topics.pendingUtbetalinger.produce(uid.toString(), mapOf("hash_key" to hashKey)) {
            TestData.utbetaling(uid = uid, sakId = sakId, originalKey = transaction, fagsystem = Fagsystem.AAP)
        }
        TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
            .has(transaction, value = StatusReply(Status.HOS_OPPDRAG, Detaljer(ytelse = Fagsystem.AAP, linjer = listOf(
                DetaljerLinje(bid, 1.nov, 14.nov, null, 1000u, "AAPOR"),
            ))))

        oppdrag.mmel = TestData.ok()
        TestRuntime.topics.oppdrag.produce(transaction, mapOf("maxRetries" to "2")) { oppdrag }

        val statusAfterKvittering = TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
        statusAfterKvittering.with(transaction) { reply ->
            assertNotEquals(Status.OK, reply.status, "Barrier must not leak Status.OK while sakerAck is false")
        }

        TestRuntime.topics.retryKvittering.assertThat()

        val dao = runBlocking {
            withContext(TestRuntime.context) {
                transaction { DaoOppdrag.findWithLockOrLegacy(hashKey, oppdrag) }
            }
        }
        assertNotNull(dao)
        assertEquals(false, dao.sakerAck)
        assertNull(dao.sakerAckAt)
    }
}
