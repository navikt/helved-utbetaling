package peisschtappern

import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import java.time.Instant
import java.util.UUID
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

        val fom = now.minusSeconds(5L).toEpochMilli()
        val tom = now.plusSeconds(5L).toEpochMilli()

        val result = httpClient.get("/api?fom=$fom&tom=$tom&key=$key") {
            accept(ContentType.Application.Json)
        }.body<List<Dao>>()

        assertEquals(1, result.size)
        assertEquals(Topics.utbetalinger.name, result[0].topic_name)
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

