package peisschtappern

import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import libs.kafka.Topic
import libs.postgres.concurrency.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertTrue(result.map { it.topic_name }.containsAll(listOf(Topics.utbetalinger.name, Topics.simuleringer.name)))
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

    private suspend fun save(
        channel: Channel,
        key: String = UUID.randomUUID().toString(),
    ) {
        val dao = Dao(
            topic_name = channel.topic.name,
            version = "v1",
            key = key,
            value = UUID.randomUUID().toString(),
            partition = 0,
            offset = 1,
            timestamp_ms = Instant.now().toEpochMilli(),
            stream_time_ms = Instant.now().toEpochMilli(),
            system_time_ms = Instant.now().toEpochMilli()
        )

        transaction {
            dao.insert(channel.table)
        }
    } 
}

