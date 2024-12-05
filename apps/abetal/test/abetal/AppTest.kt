package abetal

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals

val Int.jan: LocalDate get() = LocalDate.of(2025, 1, this)

internal class AppTest {
    // @Test
    // fun `routing to utbetalinger`() = runTest {
    //     val key = UUID.randomUUID().toString()
    //     httpClient.post("/utbetalinger/$key") {
    //         contentType(ContentType.Application.Json)
    //         setBody(UtbetalingApi.default())
    //     }.also {
    //         assertEquals(HttpStatusCode.Created, it.status)
    //     }
    //     TestTopics.status.assertThat().hasKey(key).hasValue(UtbetalingStatusApi(Status.IKKE_PÅBEGYNT))
    //     TestTopics.utbetalinger.assertThat().hasKey(key)
    //     TestTopics.oppdrag.assertThat().hasKey(key)
    // }

    @Test
    fun `dagpenger to utbetalinger`() {
        val key = UUID.randomUUID().toString()
        TestTopics.dagpenger.produce(key) {
            UtbetalingApi.default()
        }

        TestTopics.status.assertThat().hasKey(key).hasValue(UtbetalingStatusApi(Status.IKKE_PÅBEGYNT))
        TestTopics.utbetalinger.assertThat().hasKey(key)
        TestTopics.oppdrag.assertThat().hasKey(key)
    }
}

private fun UtbetalingApi.Companion.default() = UtbetalingApi(
    sakId = "123",
    behandlingId = "1",
    personident = "12345678910",
    vedtakstidspunkt = LocalDateTime.now(),
    stønad = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
    beslutterId = "test",
    saksbehandlerId = "test",
    perioder = listOf(
        UtbetalingsperiodeApi(1.jan, 1.jan, 700u),
        UtbetalingsperiodeApi(2.jan, 2.jan, 700u),
        UtbetalingsperiodeApi(3.jan, 3.jan, 700u),
    )
)
