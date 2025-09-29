package urskog

import models.*
import no.trygdeetaten.skjema.oppdrag.Mmel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.time.toDuration

class UrskogTest {

    @AfterEach 
    fun cleanup() {
        receivedMqOppdrag.clear()
    }

    private var seq: Int = 0
        get() = field++

    @Test
    fun `send to mq`() {
        val uid = UUID.randomUUID().toString()
        val pid = PeriodeId().toString()
        val bid = "$seq"

        val oppdrag = TestData.oppdrag(
            fagsystemId = "$seq",
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = bid,
                    delytelsesId = pid,
                    klassekode = "AAPOR",
                    datoVedtakFom = LocalDate.of(2025, 11, 3),
                    datoVedtakTom = LocalDate.of(2025, 11, 7),
                    typeSats = "DAG",
                    sats = 700L,
                )
            ),
        )
        TestRuntime.topics.oppdrag.produce(uid) {
            oppdrag
        }

        val kvitteringTopic = TestRuntime.kafka.getProducer(Topics.kvittering)
        assertEquals(1, kvitteringTopic.history().size)
        assertEquals(0, kvitteringTopic.uncommitted().size)

        // because streams and vanilla kafka producer is not connected by TestTopologyDriver,
        // we will manually add a kvittering to see the rest of the stream
        TestRuntime.topics.kvittering.produce(OppdragForeignKey.from(oppdrag)) {
            oppdrag.apply {
                mmel = Mmel().apply {
                    alvorlighetsgrad = "00" // 00/04/08/12
                    // kodeMelding = "" // FIXME: disse er bare satt ved 04 eller 12
                    // beskrMelding = "" // FIXME: disse er bare satt ved 04 eller 12
                }
            }
        }
        testLog.info("test complete")

        TestRuntime.topics.oppdrag.assertThat()
            .has(uid)
            .with(uid, 0) {
                assertEquals("00", it.mmel.alvorlighetsgrad)
            }

        TestRuntime.topics.status.assertThat()
            .has(uid, size = 2)
            .with(uid, index = 0) {
                assertEquals(Status.HOS_OPPDRAG, it.status)
            }
            .with(uid, index = 1) {
                val expectedDetaljer =  Detaljer(
                    ytelse = Fagsystem.AAP,
                    linjer = listOf(
                        DetaljerLinje(
                            behandlingId = bid, 
                            fom = LocalDate.of(2025, 11, 3),
                            tom = LocalDate.of(2025, 11, 7),
                            beløp = 700u,
                            vedtakssats = null,
                            klassekode = "AAPOR"
                        )
                    )
                )
                assertEquals(Status.OK, it.status)
                assertEquals(expectedDetaljer, it.detaljer)
            }
    }

    @Test
    fun `kafka can dedup mq messages`() {
        val uid = UUID.randomUUID().toString()
        val pid = PeriodeId().toString()
        val bid = "$seq"

        val oppdrag = TestData.oppdrag(
            fagsystemId = "$seq",
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = bid,
                    delytelsesId = pid,
                    klassekode = "AAPOR",
                    datoVedtakFom = LocalDate.of(2025, 11, 3),
                    datoVedtakTom = LocalDate.of(2025, 11, 7),
                    typeSats = "DAG",
                    sats = 700L,
                )
            ),
        )
        TestRuntime.topics.oppdrag.produce(uid) {
            oppdrag
        }
        TestRuntime.topics.oppdrag.produce(uid) {
            oppdrag
        }
        assertEquals(1, receivedMqOppdrag.size)
    }

    @Test
    fun `kafka respects dedup retention`() {
        val uid = UUID.randomUUID().toString()
        val pid = PeriodeId().toString()
        val bid = "$seq"

        val oppdrag = TestData.oppdrag(
            fagsystemId = "$seq",
            fagområde = "AAP",
            oppdragslinjer = listOf(
                TestData.oppdragslinje(
                    henvisning = bid,
                    delytelsesId = pid,
                    klassekode = "AAPOR",
                    datoVedtakFom = LocalDate.of(2025, 11, 3),
                    datoVedtakTom = LocalDate.of(2025, 11, 7),
                    typeSats = "DAG",
                    sats = 700L,
                )
            ),
        )
        TestRuntime.topics.oppdrag.produce(uid) {
            oppdrag
        }

        // advance klokka til noe lengere enn det som er satt som retention på dedup-processor 
        TestRuntime.kafka.advanceWallClockTime(61.toDuration(kotlin.time.DurationUnit.MINUTES))

        TestRuntime.topics.oppdrag.produce(uid) {
            oppdrag
        }

        assertEquals(2, receivedMqOppdrag.size)
    }
}

