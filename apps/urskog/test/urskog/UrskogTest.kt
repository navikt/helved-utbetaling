package urskog

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import no.trygdeetaten.skjema.oppdrag.Mmel
import models.*

class UrskogTest {
    private var seq: Int = 0
        get() = field++

    @Test
    fun `send to mq`() {
        val uid = UUID.randomUUID().toString()

        val oppdrag = TestData.oppdrag(
            fagsystemId = "$seq",
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = "$seq",
                    delytelsesId = PeriodeId().toString(),
                    klassekode = "AAPUAA",
                    datoVedtakFom = LocalDate.of(2025, 11, 3),
                    datoVedtakTom = LocalDate.of(2025, 11, 7),
                    typeSats = "DAG",
                    sats = 700L,
                )
            ),
        )
        TestTopics.oppdrag.produce(uid) {
            oppdrag
        }

        val kvitteringQueueTopic = TestRuntime.kafka.getProducer(Topics.kvitteringQueue)
        assertEquals(1, kvitteringQueueTopic.history().size)
        assertEquals(0, kvitteringQueueTopic.uncommittedRecords().size)

        // because streams and vanilla kafka producer is not connected by TestTopologyDriver,
        // we will manually add a kvittering to see the rest of the stream
        TestTopics.kvitteringQueue.produce(OppdragForeignKey.from(oppdrag)) {
            oppdrag.apply {
                mmel = Mmel().apply {
                    alvorlighetsgrad = "00" // 00/04/08/12
                    kodeMelding = "" // FIXME: er disse alltid satt i miljø?
                    beskrMelding = "" // FIXME: er disse alltid satt i miljø?
                }
            }
        }

        TestTopics.kvittering.assertThat()
            .hasNumberOfRecordsForKey(uid, 1)
            .hasValueMatching(uid, 0) {
                assertEquals("00", it.mmel.alvorlighetsgrad)
            }

        TestTopics.status.assertThat()
            .hasNumberOfRecordsForKey(uid, 2)
            .hasValueMatching(uid, 0) {
                assertEquals(Status.HOS_OPPDRAG, it.status)
            }
            .hasValueMatching(uid, 1) {
                assertEquals(Status.OK, it.status)
            }
    }
}

