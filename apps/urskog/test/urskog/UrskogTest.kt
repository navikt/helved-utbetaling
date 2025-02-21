package urskog

import urskog.models.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import com.ibm.mq.jms.MQQueue

class UrskogTest {
    private var seq: Int = 0 
        get() = field++ 

    @Test
    fun `send to mq`() {
        val uid = UUID.randomUUID()
        val sakId = "$seq"
        val behId = "$seq"
        TestTopics.utbetalinger.produce(uid.toString()) {
            Utbetaling(
                uid = uid,
                sakId = sakId,
                behandlingId = behId,
                stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            )
        }

        val oppdrag = TestData.oppdrag(
            fagsystemId = sakId,
            fagområde = "AAP", 
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = behId,

                    delytelsesId = "a",
                    klassekode = "AAPUAA",
                    datoVedtakFom =  LocalDate.of(2025, 11, 3),
                    datoVedtakTom = LocalDate.of(2025, 11, 7),
                    typeSats = "DAG",
                    sats = 700L,
                )
            ),
        )

        val keystore = TestRuntime.kafka.getStore<OppdragForeignKey, UUID>(StateStores.keystore)
        // val fk = keystore.getOrNull(OppdragForeignKey(Fagsystem.AAP, sakId, behId))
        val fk = keystore.getOrNull(OppdragForeignKey.from(oppdrag))
        assertEquals(uid, fk)

        TestTopics.oppdrag.produce(uid.toString()) {
            oppdrag
        }

        val received = TestRuntime.oppdrag.oppdragskø.received
        assertEquals(1, received.size)

        var size: Int
        val queue = MQQueue(TestRuntime.config.oppdrag.kvitteringsKø)
        val mq = TestRuntime.oppdrag.mq
        do { size = mq.depth(queue) } while (size > 0)
        val kvitteringTopic = TestRuntime.kafka.getProducer(Topics.kvittering)
        assertEquals(1, kvitteringTopic.history().size)
        assertEquals(0, kvitteringTopic.uncommittedRecords().size)
    }
}

