package peisschtappern

import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import java.time.Instant
import java.util.UUID
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import kotlin.test.*

class ApiTest {
    @Test
    fun `can query with limit`() = runTest(TestRuntime.context) {
        save(Channel.Aap)
        save(Channel.Utbetalinger)
        save(Channel.Simuleringer)

        val result = httpClient.get("/api?limit=2") {
            accept(ContentType.Application.Json)
        }.body<List<Dao>>()

        assertEquals(2, result.size)
    }

    @Test
    fun `can query for key`() = runTest(TestRuntime.context) {
        save(Channel.Aap, "testkey")
        save(Channel.Utbetalinger, "testkey")
        save(Channel.Simuleringer)

        val result = httpClient.get("/api?key=testkey") {
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

        val result = httpClient.get("/api?topics=helved.utbetalinger-aap.v1,helved.simuleringer.v1") {
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

        val result = httpClient.get("/api?value=$sakId") {
            accept(ContentType.Application.Json)
        }.body<List<Dao>>()

        assertEquals(1, result.size)
        assertTrue(result.first().value!!.contains(sakId))
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

        val result = httpClient.get("/api?fom=$fom&tom=$tom&key=$key") {
            accept(ContentType.Application.Json)
        }.body<List<Dao>>()

        assertEquals(1, result.size)
        assertEquals(Topics.utbetalinger.name, result[0].topic_name)
    }

    @Test
    fun `test kvittering endpoint overwrites oppdrag with manual kvittering`() = runTest(TestRuntime.context) {
        val xmlMapper = XMLMapper<Oppdrag>()
        val initialOppdrag = xmlMapper.readValue(TestData.oppdragXml)

        val oppdragProducer = TestRuntime.vanillaKafka.createProducer(TestRuntime.config.kafka, oppdrag)
        oppdragProducer.send("202503271001", initialOppdrag)

        val kvitteringRequest = KvitteringRequest(
            oppdragXml = TestData.oppdragXml,
            messageKey = "202503271001",
            alvorlighetsgrad = "00",
            beskrMelding = "Test",
            kodeMelding = "Test"
        )

        httpClient.post("/manuell-kvittering") {
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
            system_time_ms = timestamp
        )

        transaction {
            dao.insert(channel.table)
        }
    }
}

