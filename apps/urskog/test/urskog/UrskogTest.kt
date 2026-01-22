package urskog

import models.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals

class UrskogTest {

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

        assertEquals(1, TestRuntime.mq.sent().size)
        XmlAssert.assertEquals(oppdrag, TestRuntime.mq.sent().first())
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
        assertEquals(0, TestRuntime.mq.sent().size)

        val hashKey = DaoOppdrag.hash(oppdrag).toString()
        TestRuntime.topics.pendingUtbetalinger.produce(uid.toString(), mapOf("hash_key" to hashKey)) {
            utbetaling(uid = uid, originalKey = transaction)
        }

        TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
            .hasHeader(transaction, FS_KEY to "AAP")
            .has(transaction, value = StatusReply(Status.HOS_OPPDRAG, Detaljer(ytelse = Fagsystem.AAP, linjer = listOf(
                DetaljerLinje(bid, 1.nov, 14.nov, null, 1000u, "AAPOR"),
            ))))

        assertEquals(1, TestRuntime.mq.sent().size)
        XmlAssert.assertEquals(oppdrag, TestRuntime.mq.sent().first())
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


        val hashKey = DaoOppdrag.hash(oppdrag).toString()
        TestRuntime.topics.pendingUtbetalinger.produce(uid.toString(), mapOf("hash_key" to hashKey)) {
            utbetaling(uid = uid)
        }

        TestRuntime.topics.status.assertThat().isEmpty()
        assertEquals(0, TestRuntime.mq.sent().size)

        TestRuntime.topics.oppdrag.produce(transaction, mapOf("uids" to uid.toString())) {
            oppdrag
        }
        
        TestRuntime.topics.status.assertThat()
            .has(transaction, size = 1)
            .hasHeader(transaction, FS_KEY to "AAP")
            .has(transaction, value = StatusReply(Status.HOS_OPPDRAG, Detaljer(ytelse = Fagsystem.AAP, linjer = listOf(
                DetaljerLinje(bid, 1.nov, 14.nov, null, 1000u, "AAPOR"),
            ))))

        assertEquals(1, TestRuntime.mq.sent().size)
        XmlAssert.assertEquals(oppdrag, TestRuntime.mq.sent().first())
    }

    @Test
    fun `can receive kvittering from MQ `() {
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

        TestRuntime.topics.oppdrag.produce(uid) {
            kvittering
        }

        TestRuntime.topics.status.assertThat()
            .has(uid, size = 1)
            .hasHeader(uid, FS_KEY to "AAP")
            .has(uid, value = StatusReply(Status.OK, Detaljer(ytelse = Fagsystem.AAP, linjer = listOf(
                DetaljerLinje(bid, 3.nov, 7.nov, null, 700u, "AAPOR"),
            ))))
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

        assertEquals(1, TestRuntime.mq.sent().size)
        XmlAssert.assertEquals(oppdrag, TestRuntime.mq.sent().first())

        TestRuntime.topics.oppdrag.produce(transaction) {
            oppdrag
        }

        TestRuntime.topics.status.assertThat().isEmpty()
        assertEquals(1, TestRuntime.mq.sent().size)
    }
}

