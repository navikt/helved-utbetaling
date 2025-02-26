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
        TestTopics.utbetalinger.produce(uid.toString()) {
            Utbetaling(
                simulate = false,
                action = Action.CREATE,
                uid = uid,
                sakId = sakId,
                behandlingId = behId,
                stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                førsteUtbetalingPåSak = true,
                lastPeriodeId = PeriodeId(),
                personident = Personident(""),
                vedtakstidspunkt = LocalDateTime.now(),
                beslutterId = Navident(""),
                saksbehandlerId = Navident(""),
                periodetype = Periodetype.DAG,
                perioder = listOf(),
            )
        }

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

        val keystore = TestRuntime.kafka.getStore(Stores.keystore)
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

