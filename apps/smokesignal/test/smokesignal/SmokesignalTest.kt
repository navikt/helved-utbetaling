package smokesignal

import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.LocalDate
import kotlinx.coroutines.runBlocking

class SmokesignalTest {

    @Test
    fun `will first request next then send the signal`() {
        val fake = AvstemmingRequest(
            today = LocalDate.of(2026, 2, 10),
            fom = LocalDate.of(2026, 2, 9).atStartOfDay(),
            tom = LocalDate.of(2026, 2, 10).atStartOfDay().minusNanos(1),
        )

        TestRuntime.vedskiva.respondWith = fake

        runBlocking {
            smokesignal(
                TestRuntime.config,
                TestRuntime.vedskiva,
            )
        }

        assertEquals(TestRuntime.vedskiva.signals.size, 1)
        assertEquals(TestRuntime.vedskiva.signals.first(), fake)
    }
}
