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
