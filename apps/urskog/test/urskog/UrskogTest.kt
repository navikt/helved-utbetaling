package urskog

import models.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import com.ibm.mq.jms.MQQueue

class UrskogTest {
    private var seq: Int = 0 
        get() = field++ 

    @Test
    fun `send to mq`() {
        val uid = UtbetalingId(UUID.randomUUID())
        val sakId = SakId("$seq")
        val behId = BehandlingId("$seq")

        val oppdrag = TestData.oppdrag(
            fagsystemId = sakId.id,
            fagområde = "AAP", 
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = behId.id,
                    delytelsesId = "a",
                    klassekode = "AAPUAA",
                    datoVedtakFom =  LocalDate.of(2025, 11, 3),
                    datoVedtakTom = LocalDate.of(2025, 11, 7),
                    typeSats = "DAG",
                    sats = 700L,
                )
            ),
        )

        TestTopics.oppdrag.produce(uid.id.toString()) {
            oppdrag
        }

        val keystore = TestRuntime.kafka.getStore(Stores.keystore)
        val fk = keystore.getOrNull(OppdragForeignKey.from(oppdrag))
        assertEquals(uid, fk)
        Thread.sleep(1000) // TEST: make test complete while testing race-condition in kvitterinMqConsumer
        val kvitteringTopic = TestRuntime.kafka.getProducer(Topics.kvittering)
        assertEquals(1, kvitteringTopic.history().size)
        assertEquals(0, kvitteringTopic.uncommittedRecords().size)
    }
}

