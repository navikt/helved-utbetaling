package simulering

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.channels.Channel
import libs.kafka.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Isolated
class SimuleringSchedulerTest {
    @Test
    fun `scheduler preserves headers and removes fresh entries with headers store`() {
        verifyHeadersScheduler(expired = false, channelCapacity = 16)
    }

    @Test
    fun `scheduler preserves headers when expiring entries with headers store`() {
        verifyHeadersScheduler(expired = true, channelCapacity = 16)
    }

    @Test
    fun `scheduler preserves headers when channel is full`() {
        verifyHeadersScheduler(expired = false, channelCapacity = 0)
    }

    @Test
    fun `scheduler evicts entries older than evictionTtl`() {
        val channel = Channel<SimuleringRequestWrapper>(16)
        val backpressure = Channel<SimuleringBackpressure>(Channel.UNLIMITED)
        Names.clear()
        val kafka = StreamsMock()
        kafka.connect(
            topology = topology {
                val table = consume(Tables.simuleringer)
                table.schedule(SimuleringScheduler(table, 5.seconds, channel, backpressure, 2.minutes, null))
            },
            config = kafka.config,
            registry = SimpleMeterRegistry()
        )
        try {
            kafka.testInputTopic(Topics.simuleringer).produce("key-1") { simulering(fagområde = "AAP") }
            kafka.advanceWallClockTime(3.minutes)
            assertTrue(channel.tryReceive().isFailure, "Entry older than 2min should be evicted")
            assertEquals("key-1", backpressure.tryReceive().getOrThrow().key)
            assertNull(kafka.getStore(Store(Tables.simuleringer)).getOrNull("key-1"))
        } finally {
            kafka.close()
            channel.close()
            backpressure.close()
        }
    }

    private fun verifyHeadersScheduler(expired: Boolean, channelCapacity: Int) {
        val channel = Channel<SimuleringRequestWrapper>(channelCapacity)
        val backpressure = Channel<SimuleringBackpressure>(Channel.UNLIMITED)
        Names.clear()
        val kafka = StreamsMock()
        kafka.connect(
            topology = topology {
                val table = consume(Tables.simuleringer, simuleringHeadersProcessor(Stores.simuleringHeaders))
                table.schedule(SimuleringScheduler(table, 5.seconds, channel, backpressure))
            },
            config = kafka.config,
            registry = SimpleMeterRegistry()
        )
        try {
            kafka.advanceWallClockTime(5.seconds)
            assertTrue(channel.tryReceive().isFailure)
            assertTrue(backpressure.tryReceive().isFailure)
            val headers = mapOf("traceparent" to "00-5061e1ada666e5ef9a64424ea1af42a4-0123456789abcdef-01")
            kafka.testInputTopic(Topics.simuleringer).produce("key", headers) { simulering(fagområde = "AAP") }
            kafka.advanceWallClockTime(if (expired) 3.minutes else 5.seconds)

            if (expired || channelCapacity == 0) {
                assertEquals(headers, backpressure.tryReceive().getOrThrow().headers)
                assertTrue(channel.tryReceive().isFailure)
            } else {
                assertEquals(headers, channel.tryReceive().getOrThrow().headers)
                assertTrue(backpressure.tryReceive().isFailure)
            }
            assertNull(kafka.getStore(Stores.simuleringHeaders).getOrNull("key"))
            assertNull(kafka.getStore(Store(Tables.simuleringer)).getOrNull("key"))
            kafka.advanceWallClockTime(5.seconds)
            assertTrue(channel.tryReceive().isFailure)
            assertTrue(backpressure.tryReceive().isFailure)
        } finally {
            kafka.close()
            channel.close()
            backpressure.close()
        }
    }
}

