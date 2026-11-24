package simulering

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import libs.kafka.KafkaProducerFake
import libs.utils.Resource
import models.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@Isolated
class SimuleringWorkerTest {
    private val service = SimuleringService(TestRuntime, TestRuntime)

    @BeforeEach
    fun setup() {
        TestRuntime.reset()
        TestRuntime.soapRespondWith(Resource.read("/simuler-jaxb-response.xml"))
    }

    @Test
    fun `worker continues after fagsystem parsing failure`() {
        val producer = KafkaProducerFake(Topics.dryrunAap)
        runWorker(
            mapOf(Fagsystem.AAP to producer),
            SimuleringRequestWrapper("bad-key", simulering(fagområde = "UGYLDIG")),
            SimuleringRequestWrapper("good-key", simulering(fagområde = "AAP"))
        )

        assertEquals("good-key", producer.history().single().first)
    }

    @Test
    fun `worker continues after producer send failure`() {
        val producer = KafkaProducerFake(Topics.dryrunAap)
        // No producer for DAGPENGER: producerFor fails before sending.
        runWorker(
            mapOf(Fagsystem.AAP to producer),
            SimuleringRequestWrapper("dp-key", simulering(fagområde = "DP")),
            SimuleringRequestWrapper("aap-key", simulering(fagområde = "AAP"))
        )

        assertEquals("aap-key", producer.history().single().first)
    }

    @Test
    fun `worker sends simulation without headers for requests stored before header sidecar deployment`() {
        val producer = KafkaProducerFake(Topics.dryrunAap)
        runWorker(
            mapOf(Fagsystem.AAP to producer),
            SimuleringRequestWrapper("legacy-key", simulering(fagområde = "AAP"))
        )

        val (key, _, headers) = producer.historyWithHeaders().single()
        assertEquals("legacy-key", key)
        assertEquals(emptyMap(), headers)
    }

    private fun runWorker(
        producers: Map<Fagsystem, KafkaProducerFake<String, Simulering>>,
        vararg requests: SimuleringRequestWrapper
    ) = runBlocking {
        val channel = Channel<SimuleringRequestWrapper>(requests.size)
        val backpressure = Channel<SimuleringBackpressure>(Channel.UNLIMITED)
        val worker = SimuleringWorker(channel, backpressure, service, producers)
        requests.forEach { channel.send(it) }
        channel.close()
        try {
            withTimeout(5.seconds) {
                launch { worker.run() }.join()
            }
        } finally {
            channel.cancel()
            backpressure.cancel()
        }
    }
}

