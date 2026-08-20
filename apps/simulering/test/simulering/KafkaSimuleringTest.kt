package simulering

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import libs.kafka.KafkaProducerFake
import libs.kafka.StreamsMock
import libs.utils.Resource
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class KafkaSimuleringTest {
    private val service = SimuleringService(TestRuntime, TestRuntime)

    @BeforeEach
    fun setup() {
        TestRuntime.reset()
    }

    @Test
    fun `simulering for TS produserer v1 resultat til dryrun-ts topic`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = freshProducers()
        val worker = SimuleringWorker(channel, service, producers)

        TestRuntime.soapRespondWith(jaxbResponse())
        channel.trySend("test-key" to simulering(fagområde = "TILLST"))

        runWorkerUntilProduced(worker, channel, producers)

        val history = producers[Fagsystem.TILLEGGSSTØNADER]!!.history()
        assertEquals(1, history.size)
        val (key, result) = history.first()
        assertEquals("test-key", key)
        assertTrue(result is v1.Simulering)
    }

    @Test
    fun `simulering for AAP produserer v2 resultat til dryrun-aap topic`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = freshProducers()
        val worker = SimuleringWorker(channel, service, producers)

        TestRuntime.soapRespondWith(jaxbResponse())
        channel.trySend("aap-key" to simulering(fagområde = "AAP"))

        runWorkerUntilProduced(worker, channel, producers)

        val history = producers[Fagsystem.AAP]!!.history()
        assertEquals(1, history.size)
        val (key, result) = history.first()
        assertEquals("aap-key", key)
        assertTrue(result is v2.Simulering)
    }

    @Test
    fun `SOAP fault produserer Info med UGYLDIG_REQUEST`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = freshProducers()
        val worker = SimuleringWorker(channel, service, producers)

        val fault = """
            <faultcode>soap:Server</faultcode>
            <faultstring>simulerBeregningFeilUnderBehandling</faultstring>
            <detail>
                <sf:simulerBeregningFeilUnderBehandling xmlns:sf="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
                    <errorMessage>Personen finnes ikke</errorMessage>
                </sf:simulerBeregningFeilUnderBehandling>
            </detail>
        """.trimIndent()
        TestRuntime.soapRespondWith(soapFault(fault))
        channel.trySend("dp-key" to simulering(fagområde = "DP"))

        runWorkerUntilProduced(worker, channel, producers)

        val history = producers[Fagsystem.DAGPENGER]!!.history()
        assertEquals(1, history.size)
        val (key, result) = history.first()
        assertEquals("dp-key", key)
        assertTrue(result is Info)
        assertEquals(Info.Status.UGYLDIG_REQUEST, result.status)
    }

    @Test
    fun `tom SOAP-respons produserer Info OkUtenEndring for TS`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = freshProducers()
        val worker = SimuleringWorker(channel, service, producers)

        TestRuntime.soapRespondWith("""
            <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt"></simulerBeregningResponse>
        """.trimIndent())
        channel.trySend("ts-key" to simulering(fagområde = "TILLST"))

        runWorkerUntilProduced(worker, channel, producers)

        val history = producers[Fagsystem.TILLEGGSSTØNADER]!!.history()
        assertEquals(1, history.size)
        val (_, result) = history.first()
        assertTrue(result is Info)
        assertEquals(Info.Status.OK_UTEN_ENDRING, result.status)
    }

    @Test
    fun `simuler sak 200001495 produserer korrekt v1 resultat`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = freshProducers()
        val worker = SimuleringWorker(channel, service, producers)

        TestRuntime.soapRespondWith(Resource.read("/simuler-ts-200001495.xml"))
        channel.trySend("ts-key" to simulering(fagområde = "TILLST"))

        runWorkerUntilProduced(worker, channel, producers)

        val (key, result) = producers[Fagsystem.TILLEGGSSTØNADER]!!.history().single()
        assertEquals("ts-key", key)
        assertTrue(result is v1.Simulering)
    }

    @Test
    fun `simuler sak 200001495 produserer korrekt v2 resultat`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = freshProducers()
        val worker = SimuleringWorker(channel, service, producers)

        TestRuntime.soapRespondWith(Resource.read("/simuler-ts-200001495.xml"))
        channel.trySend("aap-key" to simulering(fagområde = "AAP"))

        runWorkerUntilProduced(worker, channel, producers)

        val (key, result) = producers[Fagsystem.AAP]!!.history().single()
        assertEquals("aap-key", key)
        assertTrue(result is v2.Simulering)
    }

    @Test
    fun `simuler sak 4819 produserer korrekt v1 resultat`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = freshProducers()
        val worker = SimuleringWorker(channel, service, producers)

        TestRuntime.soapRespondWith(Resource.read("/simuler-ts-4819.xml"))
        channel.trySend("ts-key" to simulering(fagområde = "TILLST"))

        runWorkerUntilProduced(worker, channel, producers)

        val (key, result) = producers[Fagsystem.TILLEGGSSTØNADER]!!.history().single()
        assertEquals("ts-key", key)
        assertTrue(result is v1.Simulering)
    }

    @Test
    fun `simuler sak 4819 produserer korrekt v2 resultat`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = freshProducers()
        val worker = SimuleringWorker(channel, service, producers)

        TestRuntime.soapRespondWith(Resource.read("/simuler-ts-4819.xml"))
        channel.trySend("aap-key" to simulering(fagområde = "AAP"))

        runWorkerUntilProduced(worker, channel, producers)

        val (key, result) = producers[Fagsystem.AAP]!!.history().single()
        assertEquals("aap-key", key)
        assertTrue(result is v2.Simulering)
    }

    @Test
    fun `scheduler evicts entries older than evictionTtl`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)

        libs.kafka.Names.clear()
        val kafka = StreamsMock()
        kafka.connect(
            topology = libs.kafka.topology {
                val ktable = consume(Tables.simuleringer)
                val scheduler = SimuleringScheduler(ktable, 5.milliseconds, channel, 2.minutes)
                ktable.schedule(scheduler)
            },
            config = kafka.config.copy(additionalProperties = java.util.Properties().apply {
                put(org.apache.kafka.streams.StreamsConfig.DSL_STORE_SUPPLIERS_CLASS_CONFIG,
                    org.apache.kafka.streams.state.BuiltInDslStoreSuppliers.InMemoryDslStoreSuppliers::class.java)
            }),
            registry = SimpleMeterRegistry(),
        )

        val topic = kafka.testInputTopic(Topics.simuleringer)
        topic.produce("key-1") { simulering(fagområde = "AAP") }

        kafka.advanceWallClockTime(3.minutes)
        assertTrue(channel.tryReceive().isFailure, "Entry older than 2min should be evicted")
    }

    @Test
    fun `worker continues after fagsystem parsing failure`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = freshProducers()
        val worker = SimuleringWorker(channel, service, producers)

        TestRuntime.soapRespondWith(jaxbResponse())
        channel.trySend("bad-key" to simulering(fagområde = "UGYLDIG"))
        channel.trySend("good-key" to simulering(fagområde = "AAP"))

        runWorkerUntilProduced(worker, channel, producers)

        val history = producers[Fagsystem.AAP]!!.history()
        assertEquals(1, history.size)
        assertEquals("good-key", history.first().first)
    }

    @Test
    fun `worker continues after producer send failure`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)
        val producers = freshProducers()
        // No producer for DAGPENGER — producerFor will throw
        val incompleteProducers = producers.filterKeys { it != Fagsystem.DAGPENGER }
        val worker = SimuleringWorker(channel, service, incompleteProducers)

        TestRuntime.soapRespondWith(jaxbResponse())
        channel.trySend("dp-key" to simulering(fagområde = "DP"))
        channel.trySend("aap-key" to simulering(fagområde = "AAP"))

        runWorkerUntilProduced(worker, channel, incompleteProducers)

        val history = incompleteProducers[Fagsystem.AAP]!!.history()
        assertEquals(1, history.size)
        assertEquals("aap-key", history.first().first)
    }

    @Test
    fun `full dryrun round-trip via kafka topology`() {
        val channel = Channel<Pair<String, SimulerBeregningRequest>>(Channel.UNLIMITED)

        libs.kafka.Names.clear()
        val kafka = StreamsMock()
        kafka.connect(
            topology = libs.kafka.topology {
                simuleringer(channel)
            },
            config = kafka.config.copy(additionalProperties = java.util.Properties().apply {
                put(org.apache.kafka.streams.StreamsConfig.DSL_STORE_SUPPLIERS_CLASS_CONFIG,
                    org.apache.kafka.streams.state.BuiltInDslStoreSuppliers.InMemoryDslStoreSuppliers::class.java)
            }),
            registry = SimpleMeterRegistry(),
        )

        // 1. Produce SimulerBeregningRequest to simuleringer topic
        val inputTopic = kafka.testInputTopic(Topics.simuleringer)
        inputTopic.produce("round-trip-key") { simulering(fagområde = "AAP") }

        // 2. Advance wall clock so scheduler fires (interval=5s)
        kafka.advanceWallClockTime(6.seconds)

        // 3. Verify channel received the entry
        val received = channel.tryReceive()
        assertTrue(received.isSuccess, "Scheduler should have sent entry to channel")
        assertEquals("round-trip-key", received.getOrThrow().first)

        // 4. Run worker with the received entry — produces to dryrun topic
        val producers = freshProducers()
        val worker = SimuleringWorker(channel, service, producers)
        TestRuntime.soapRespondWith(jaxbResponse())
        channel.trySend(received.getOrThrow())

        runWorkerUntilProduced(worker, channel, producers)

        val workerHistory = producers[Fagsystem.AAP]!!.history()
        assertEquals(1, workerHistory.size)
        val (key, result) = workerHistory.first()
        assertEquals("round-trip-key", key)

        // 5. Feed worker output into dryrun GlobalKTable topic
        val dryrunInput = kafka.testInputTopic(Topics.dryrunAap)
        dryrunInput.produce(key) { result }

        // 6. Verify store returns the result
        val store = kafka.getStore<String, Simulering>(Stores.dryrunAap)
        val stored = store.getOrNull("round-trip-key")
        assertEquals(result, stored)
    }

    private fun runWorkerUntilProduced(
        worker: SimuleringWorker,
        channel: Channel<Pair<String, SimulerBeregningRequest>>,
        producers: Map<Fagsystem, KafkaProducerFake<String, Simulering>>,
    ) {
        runBlocking {
            val job = launch { worker.run() }
            withTimeout(5.seconds) {
                while (producers.values.all { it.history().isEmpty() }) {
                    kotlinx.coroutines.delay(10)
                }
            }
            channel.close()
            job.join()
        }
    }

    private fun freshProducers(): Map<Fagsystem, KafkaProducerFake<String, Simulering>> = mapOf(
        Fagsystem.AAP to KafkaProducerFake(Topics.dryrunAap),
        Fagsystem.DAGPENGER to KafkaProducerFake(Topics.dryrunDp),
        Fagsystem.TILLEGGSSTØNADER to KafkaProducerFake(Topics.dryrunTs),
        Fagsystem.TILTAKSPENGER to KafkaProducerFake(Topics.dryrunTp),
    )

    private fun jaxbResponse(): String = Resource.read("/simuler-jaxb-response.xml")

    private fun soapFault(body: String): String = """
        <SOAP-ENV:Fault xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
            $body
        </SOAP-ENV:Fault>
    """.trimIndent()
}
