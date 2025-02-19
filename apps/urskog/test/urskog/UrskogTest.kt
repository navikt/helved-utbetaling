package urskog

import urskog.models.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import libs.utils.secureLog

class UrskogTest {

    @Test
    fun `send to mq`() {
        TestTopics.oppdrag.produce("1") {
            TestData.oppdrag(
                oppdragslinjer = listOf(
                    TestData.oppdragslinje(
                        delytelsesId = "a",
                        sats = 700L,
                        datoVedtakFom =  LocalDate.of(2025, 11, 3),
                        datoVedtakTom = LocalDate.of(2025, 11, 7),
                        typeSats = "DAG",
                        henvisning = "123",
                    )
                ),
            )
        }
        val received = TestRuntime.oppdrag.oppdragskø.received
        assertEquals(1, received.size)
        secureLog.warn(received.first().text)
    }
}

