package snickerboa

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import models.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class SnickerboaApiTest {

    @BeforeEach
    fun reset() {
        TestRuntime.kafka.reset()
    }

    companion object {
        private val offset = AtomicLong(0)
    }

    @Test
    fun `health endpoint returns OK`() = testApplication {
        val response = TestRuntime.ktor.httpClient.get("/actuator/live")
        assertEquals(HttpStatusCode.OK, response.status)

        val ready = TestRuntime.ktor.httpClient.get("/actuator/ready")
        assertEquals(HttpStatusCode.OK, ready.status)
    }

    @Test
    fun `aap utbetaling produces to kafka and returns OK on status reply`() = runBlocking {
        val responseDeferred = async {
            TestRuntime.ktor.httpClient.post("/abetal/aap") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(aapUtbetaling())
            }
        }

        val txId = awaitProduced(TestRuntime.topics.aapIntern)
        TestRuntime.topics.status.populate(txId, StatusReply.ok(), partition = 0, offset = offset.getAndIncrement())

        assertEquals(HttpStatusCode.OK, responseDeferred.await().status)
    }

    @Test
    fun `aap dryrun produces to kafka and returns simulering`() = runBlocking {
        val responseDeferred = async {
            TestRuntime.ktor.httpClient.post("/abetal/aap") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(aapUtbetaling(dryrun = true))
            }
        }

        val txId = awaitProduced(TestRuntime.topics.aapIntern)
        TestRuntime.topics.dryrunAap.populate(txId, v2.Simulering(perioder = emptyList()), partition = 0, offset = offset.getAndIncrement())

        assertEquals(HttpStatusCode.OK, responseDeferred.await().status)
    }

    @Test
    fun `aap utbetaling returns error status when status reply is feilet`() = runBlocking {
        val responseDeferred = async {
            TestRuntime.ktor.httpClient.post("/abetal/aap") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(aapUtbetaling())
            }
        }

        val txId = awaitProduced(TestRuntime.topics.aapIntern)
        TestRuntime.topics.status.populate(
            txId,
            StatusReply.err(ApiError(422, "Ugyldig request")),
            partition = 0,
            offset = offset.getAndIncrement()
        )

        assertEquals(HttpStatusCode.UnprocessableEntity, responseDeferred.await().status)
    }
}

private suspend fun <V> awaitProduced(producer: libs.kafka.KafkaProducerFake<String, V>): String =
    withTimeout(5.seconds) {
        while (producer.history().isEmpty()) delay(10)
        producer.history().first().first
    }
