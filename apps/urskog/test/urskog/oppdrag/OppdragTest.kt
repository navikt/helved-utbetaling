package urskog.oppdrag

import models.*
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import urskog.*
import java.util.*
import kotlin.test.assertEquals

class OppdragTest {

    @AfterEach 
    fun cleanup() {
        TestRuntime.mq.reset()
        TestRuntime.topics.status.assertThat().isEmpty()
        TestRuntime.topics.oppdrag.assertThat().isEmpty()
        TestRuntime.topics.pendingUtbetalinger.assertThat().isEmpty()
    }

    @Test
    fun `can send oppdrag to mq without uids in the kafka headers`() {
        val transaction = UUID.randomUUID().toString()
        val bid = "$seq"
        val oppdrag = TestData.oppdrag(
            fagsystemId = "$seq",
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

        TestRuntime.topics.oppdrag.produce(transaction) {
            oppdrag
        }

        TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
            .hasHeader(transaction, FS_KEY to "AAP")
            .has(transaction, value = StatusReply(Status.HOS_OPPDRAG, Detaljer(ytelse = Fagsystem.AAP, linjer = listOf(
                DetaljerLinje(bid, 1.nov, 14.nov, null, 1000u, "AAPOR"),
            ))))

        assertEquals(1, TestRuntime.mq.sentOppdrag().size)
        XmlAssert.assertEquals(oppdrag, TestRuntime.mq.sentOppdrag().first())
    }

    @Test
    fun `can send oppdrag to mq with uids in the kafka headers when oppdrag arrives first`() {
        val transaction = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val bid = "$seq"
        val oppdrag = TestData.oppdrag(
            fagsystemId = "$seq",
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

        TestRuntime.topics.status.assertThat().isEmpty()
        assertEquals(0, TestRuntime.mq.sentOppdrag().size)

        val hashKey = DaoOppdrag.hash(oppdrag)
        TestRuntime.topics.pendingUtbetalinger.produce(uid.toString(), mapOf("hash_key" to hashKey)) {
            TestData.utbetaling(uid = uid, originalKey = transaction)
        }

        TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
            .hasHeader(transaction, FS_KEY to "AAP")
            .has(transaction, value = StatusReply(Status.HOS_OPPDRAG, Detaljer(ytelse = Fagsystem.AAP, linjer = listOf(
                DetaljerLinje(bid, 1.nov, 14.nov, null, 1000u, "AAPOR"),
            ))))

        assertEquals(1, TestRuntime.mq.sentOppdrag().size)
        XmlAssert.assertEquals(oppdrag, TestRuntime.mq.sentOppdrag().first())
    }

    @Test
    fun `can send oppdrag to mq with uids in the kafka headers when pending utbetaling arrives first`() {
        val transaction = UUID.randomUUID().toString()
        val uid = UtbetalingId(UUID.randomUUID())
        val bid = "$seq"
        val oppdrag = TestData.oppdrag(
            fagsystemId = "$seq",
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


        val hashKey = DaoOppdrag.hash(oppdrag)
        TestRuntime.topics.pendingUtbetalinger.produce(uid.toString(), mapOf("hash_key" to hashKey)) {
            TestData.utbetaling(uid = uid)
        }

        TestRuntime.topics.status.assertThat().isEmpty()
        assertEquals(0, TestRuntime.mq.sentOppdrag().size)

        TestRuntime.topics.oppdrag.produce(transaction, mapOf("uids" to uid.toString())) {
            oppdrag
        }
        
        TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
            .hasHeader(transaction, FS_KEY to "AAP")
            .has(transaction, value = StatusReply(Status.HOS_OPPDRAG, Detaljer(ytelse = Fagsystem.AAP, linjer = listOf(
                DetaljerLinje(bid, 1.nov, 14.nov, null, 1000u, "AAPOR"),
            ))))

        assertEquals(1, TestRuntime.mq.sentOppdrag().size)
        XmlAssert.assertEquals(oppdrag, TestRuntime.mq.sentOppdrag().first())
    }

    @Test
    fun `can receive kvittering from MQ `() {
        // Legacy MQ kvittering arrives without a prior oppdrag write. After the read-side
        // completeness barrier was introduced, an unseeded kvittering can never resolve the
        // matching oppdrag row (production correlates strictly via XML hash) and is routed
        // through retryKvittering until the retry budget is exhausted. Use a tiny maxRetries
        // header so the exhaustion path completes quickly inside TopologyTestDriver.
        val uid = UUID.randomUUID().toString()
        val bid = "$seq"

        val kvittering = TestData.oppdrag(
            mmel = TestData.ok(),
            fagsystemId = "$seq",
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = bid,
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "AAPOR",
                    datoVedtakFom = 3.nov,
                    datoVedtakTom = 7.nov,
                    typeSats = "DAG",
                    sats = 700L,
                )
            ),
        )

        TestRuntime.topics.oppdrag.produce(uid, mapOf("maxRetries" to "1")) {
            kvittering
        }

        // Barrier MUST NOT emit Status.OK for an unseeded kvittering. The exhaustion path
        // produces a single FEILET record on the status topic.
        val statusRecords = TestRuntime.topics.status.assertThat()
            .has(uid, size = 1)
            .hasHeader(uid, FS_KEY to "AAP")
        statusRecords.with(uid) { reply ->
            kotlin.test.assertEquals(Status.FEILET, reply.status, "Barrier must not leak Status.OK")
        }

        // Drain retry topic emissions produced during the retry/exhaust loop.
        TestRuntime.topics.retryKvittering.assertThat()
    }

    @Test
    fun `kafka can dedup mq messages`() {
        val transaction = UUID.randomUUID().toString()
        val bid = "$seq"

        val oppdrag = TestData.oppdrag(
            fagsystemId = "$seq",
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = bid,
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "AAPOR",
                    datoVedtakFom = 3.nov,
                    datoVedtakTom = 7.nov,
                    typeSats = "DAG",
                    sats = 700L,
                )
            ),
        )
        TestRuntime.topics.oppdrag.produce(transaction) {
            oppdrag
        }

        TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
            .hasHeader(transaction, FS_KEY to "AAP")
            .has(transaction, value = StatusReply(Status.HOS_OPPDRAG, Detaljer(ytelse = Fagsystem.AAP, linjer = listOf(
                DetaljerLinje(bid, 3.nov, 7.nov, null, 700u, "AAPOR"),
            ))))

        assertEquals(1, TestRuntime.mq.sentOppdrag().size)
        XmlAssert.assertEquals(oppdrag, TestRuntime.mq.sentOppdrag().first())

        TestRuntime.topics.oppdrag.produce(transaction) {
            oppdrag
        }

        TestRuntime.topics.status.assertThat().isEmpty()
        assertEquals(1, TestRuntime.mq.sentOppdrag().size)
    }
}
