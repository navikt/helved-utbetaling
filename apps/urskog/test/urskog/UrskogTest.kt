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
        val pid = PeriodeId().toString()

        val oppdrag = TestData.oppdrag(
            fagsystemId = "$seq",
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = "$seq",
                    delytelsesId = pid,
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

        val kvitteringTopic = TestRuntime.kafka.getProducer(Topics.kvittering)
        assertEquals(1, kvitteringTopic.history().size)
        assertEquals(0, kvitteringTopic.uncommitted().size)

        // because streams and vanilla kafka producer is not connected by TestTopologyDriver,
        // we will manually add a kvittering to see the rest of the stream
        TestTopics.kvittering.produce(OppdragForeignKey.from(oppdrag)) {
            oppdrag.apply {
                mmel = Mmel().apply {
                    alvorlighetsgrad = "00" // 00/04/08/12
                    // kodeMelding = "" // FIXME: disse er bare satt ved 04 eller 12
                    // beskrMelding = "" // FIXME: disse er bare satt ved 04 eller 12
                }
            }
        }
        testLog.info("test complete")

        TestTopics.oppdrag.assertThat()
            .has(uid)
            .with(uid, 0) {
                assertEquals("00", it.mmel.alvorlighetsgrad)
            }

        TestTopics.status.assertThat()
            .has(uid, size = 2)
            .with(uid, index = 0) {
                assertEquals(Status.HOS_OPPDRAG, it.status)
            }
            .with(uid, index = 1) {
                val expectedDetaljer =  Detaljer(
                    listOf(
                        DetaljerLinje(
                            fom = LocalDate.of(2025, 11, 3),
                            tom = LocalDate.of(2025, 11, 7),
                            beløp = 700u,
                            vedtakssats = null,
                            klassekode = "AAPUAA"
                        )
                    )
                )
                assertEquals(Status.OK, it.status)
                assertEquals(expectedDetaljer, it.detaljer)
            }
    }
}

