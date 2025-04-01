package peisstchappern

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
        transaction {
            dao(Topics.aap).insert(Tables.aap)
            dao(Topics.utbetalinger).insert(Tables.utbetalinger)
            dao(Topics.simuleringer).insert(Tables.simuleringer)
        }

        val result = httpClient.get("/api?limit=2") {
            accept(ContentType.Application.Json)
        }.body<List<Dao>>()

        assertEquals(2, result.size)
        assertTrue(result.map { it.topic_name }.containsAll(listOf(Topics.utbetalinger.name, Topics.simuleringer.name)))
    }

    @Test
    fun `can query for key`() = runTest(TestRuntime.context) {
        transaction {
            dao(Topics.aap, key = "testkey").insert(Tables.aap)
            dao(Topics.utbetalinger, key = "testkey").insert(Tables.utbetalinger)
            dao(Topics.simuleringer).insert(Tables.simuleringer)
        }

        val result = httpClient.get("/api?key=testkey") {
            accept(ContentType.Application.Json)
        }.body<List<Dao>>()

        assertEquals(2, result.size)
        assertTrue(result.map { it.topic_name }.containsAll(listOf(Topics.utbetalinger.name, Topics.aap.name)))
    }

    @Test
    fun `can query for topics`() = runTest(TestRuntime.context) {
        transaction {
            dao(Topics.aap).insert(Tables.aap)
            dao(Topics.utbetalinger).insert(Tables.utbetalinger)
            dao(Topics.simuleringer).insert(Tables.simuleringer)
        }

        val result = httpClient.get("/api?topics=helved.utbetalinger-aap.v1,helved.simuleringer.v1") {
            accept(ContentType.Application.Json)
        }.body<List<Dao>>()

        assertEquals(2, result.size)
        assertTrue(result.map { it.topic_name }.containsAll(listOf(Topics.aap.name, Topics.simuleringer.name)))
    }

    private fun dao(
        topic: Topic<String, ByteArray>,
        key: String = UUID.randomUUID().toString(),
    ) = Dao(
        topic_name = topic.name,
        version = "v1",
        key = key,
        value = UUID.randomUUID().toString(),
        partition = 0,
        offset = 1,
        timestamp_ms = Instant.now().toEpochMilli(),
        stream_time_ms = Instant.now().toEpochMilli(),
        system_time_ms = Instant.now().toEpochMilli()
    )
}