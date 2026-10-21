package smokesignal

import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.LocalDate
import kotlinx.coroutines.runBlocking

class SmokesignalTest {
    @Test
    fun `sender ikke avstemming i helgen`() = runBlocking {
        val vedskiva = object : Vedskiva {
            override suspend fun next(): AvstemmingRequest = error("Skal ikke hente periode i helgen")
            override suspend fun signal(req: AvstemmingRequest) = error("Skal ikke avstemme i helgen")
        }
        smokesignal(TestRuntime.config, vedskiva, LocalDate.of(2026, 2, 14))
    }


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
                today = fake.today,
            )
        }

        assertEquals(TestRuntime.vedskiva.signals.size, 1)
        assertEquals(TestRuntime.vedskiva.signals.first(), fake)
    }
}
