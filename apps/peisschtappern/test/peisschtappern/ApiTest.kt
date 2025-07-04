package peisschtappern

import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.test.runTest
import libs.jdbc.concurrency.transaction
import java.time.Instant
import java.util.UUID
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.junit.jupiter.api.Disabled
import kotlin.test.*

class ApiTest {
    @Test
    fun `can query with limit`() = runTest(TestRuntime.context) {
        save(Channel.Aap)
        save(Channel.Utbetalinger)
        save(Channel.Simuleringer)

        val result = TestRuntime.ktor.httpClient.get("/api?limit=2") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<List<Dao>>()

        assertEquals(2, result.size)
    }

    @Test
    fun `can query for key`() = runTest(TestRuntime.context) {
        save(Channel.Aap, "testkey")
        save(Channel.Utbetalinger, "testkey")
        save(Channel.Simuleringer)

        val result = TestRuntime.ktor.httpClient.get("/api?key=testkey") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<List<Dao>>()

        assertEquals(2, result.size)
        assertTrue(result.map { it.topic_name }.containsAll(listOf(Topics.utbetalinger.name, Topics.aap.name)))
    }

    @Test
    fun `can query for topics`() = runTest(TestRuntime.context) {
        save(Channel.Aap)
        save(Channel.Utbetalinger)
        save(Channel.Simuleringer)

        val result = TestRuntime.ktor.httpClient.get("/api?topics=helved.utbetalinger-aap.v1,helved.simuleringer.v1") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<List<Dao>>()

        assertEquals(2, result.size)
        assertTrue(result.map { it.topic_name }.containsAll(listOf(Topics.aap.name, Topics.simuleringer.name)))
    }

    @Test
    fun `can query for value`() = runTest(TestRuntime.context) {
        val sakId = "BFH123DN"
        save(Channel.Aap, value = "{\"sakId\":\"$sakId\"}")
        save(Channel.Utbetalinger)
        save(Channel.Simuleringer)

        val result = TestRuntime.ktor.httpClient.get("/api?value=$sakId") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<List<Dao>>()

        assertEquals(1, result.size)
        assertTrue(result.first().value!!.contains(sakId))
    }

    @Test
    fun `can query for multiple values`() = runTest(TestRuntime.context) {
        val sakId = "AB12345"
        val behandlingId = "BC23456"
        save(Channel.Aap, value = "{\"sakId\":\"$sakId\"}")
        save(Channel.Aap, value = "{\"behandlingId\":\"$behandlingId\"}")
        save(Channel.Utbetalinger)
        save(Channel.Simuleringer)

        val result = TestRuntime.ktor.httpClient.get("/api?value=$sakId,$behandlingId") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<List<Dao>>()

        assertEquals(2, result.size)
        assertNotNull(result.find { it.value!!.contains(sakId) })
        assertNotNull(result.find { it.value!!.contains(behandlingId) })
    }

    @Test
    fun `can query for fom, tom and value`() = runTest(TestRuntime.context) {
        val result = TestRuntime.ktor.httpClient.get("/api?fom=2025-05-21T10:48:29.336Z&tom=2025-05-28T10:48:29.336Z&value=4NiJMF4") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, result.status)
    }

    @Test
    fun `can query for fom-tom`() = runTest(TestRuntime.context) {
        val before = Instant.now().minusSeconds(10L)
        val now = Instant.now()
        val later = Instant.now().plusSeconds(10L)
        val key = UUID.randomUUID().toString()

        save(Channel.Aap, key = key, timestamp = before.toEpochMilli())
        save(Channel.Utbetalinger, key = key, timestamp = now.toEpochMilli())
        save(Channel.Simuleringer, key = key, timestamp = later.toEpochMilli())

        val fom = now.minusSeconds(5L).toString()
        val tom = now.plusSeconds(5L).toString()

        val result = TestRuntime.ktor.httpClient.get("/api?fom=$fom&tom=$tom&key=$key") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<List<Dao>>()

        assertEquals(1, result.size)
        assertEquals(Topics.utbetalinger.name, result[0].topic_name)
    }

    @Test
    fun `test kvittering endpoint overwrites oppdrag with manual kvittering`() = runTest(TestRuntime.context) {
        val xmlMapper = XMLMapper<Oppdrag>()
        val initialOppdrag = xmlMapper.readValue(TestData.oppdragXml)

        val oppdragProducer = TestRuntime.vanillaKafka.createProducer(TestRuntime.kafka.config, oppdrag)
        oppdragProducer.send("202503271001", initialOppdrag)

        val kvitteringRequest = KvitteringRequest(
            oppdragXml = TestData.oppdragXml,
            messageKey = "202503271001",
            alvorlighetsgrad = "00",
            beskrMelding = "Test",
            kodeMelding = "Test"
        )

        TestRuntime.ktor.httpClient.post("/manuell-kvittering") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(kvitteringRequest)
        }.let {
            assertEquals(HttpStatusCode.OK, it.status)
        }

        assertEquals(2, oppdragProducer.history().size)

        val updatedMessage = oppdragProducer.history().last().second

        assertNotNull(updatedMessage.mmel)
        assertEquals("00", updatedMessage.mmel.alvorlighetsgrad)
        assertEquals("Test", updatedMessage.mmel.beskrMelding)
        assertEquals("Test", updatedMessage.mmel.kodeMelding)
    }

    @Test
    fun `get saker`() = runTest(TestRuntime.context) {
        TestRuntime.ktor.httpClient.get("/api/saker") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<List<Dao>>()
    }

    @Disabled("Denne feiler om man kjører den sammen med andre tester som produserer ugyldig json/xml i db")
    @Test
    fun `get hendelser for sak`() = runTest(TestRuntime.context) {
        TestRuntime.ktor.httpClient.get("/api/saker/test/test") {
            bearerAuth(TestRuntime.azure.generateToken())
            accept(ContentType.Application.Json)
        }.body<List<Dao>>()
    }

    private suspend fun save(
        channel: Channel,
        key: String = UUID.randomUUID().toString(),
        value: String = UUID.randomUUID().toString(),
        timestamp: Long = Instant.now().toEpochMilli(),
    ) {
        val dao = Dao(
            topic_name = channel.topic.name,
            version = "v1",
            key = key,
            value = value,
            partition = 0,
            offset = 1,
            timestamp_ms = timestamp,
            stream_time_ms = timestamp,
            system_time_ms = timestamp,
            trace_id = null,
        )

        transaction {
            dao.insert(channel.table)
        }
    }
}

