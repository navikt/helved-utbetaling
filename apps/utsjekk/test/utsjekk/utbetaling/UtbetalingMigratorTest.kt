package utsjekk.utbetaling

import TestRuntime
import httpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import libs.jdbc.concurrency.transaction
import utsjekk.Topics

class UtbetalingMigratorTest {

    @Test
    fun `uid not found`() = runTest(TestRuntime.context) {
        val uid = UUID.randomUUID()
        val meldeperiode = "123"

        val res = httpClient.post("/utbetalinger/$uid/migrate") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(meldeperiode)
        }

        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `can migrate uid`() = runTest(TestRuntime.context) {
        val meldeperiode = "123"
        val uid = UUID.randomUUID()
        val data = Utbetaling.aap(
            vedtakstidspunkt = 1.mar, 
            satstype = Satstype.ENGANGS,
            perioder = listOf(Utbetalingsperiode.dagpenger(4.feb, 4.feb, 500u)),
        )

        transaction {
            UtbetalingDao(data, Status.OK).insert(UtbetalingId(uid))
        }

        val res = httpClient.post("/utbetalinger/$uid/migrate") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(meldeperiode)
        }

        assertEquals(HttpStatusCode.OK, res.status)
        val utbetalingAap = TestRuntime.kafka.getProducer(Topics.utbetalingAap)
        assertEquals(0, utbetalingAap.uncommitted().size)
        val actual = utbetalingAap.history().single { (key, _) -> key == uid.toString() }.second

        assertEquals(1, actual.utbetalinger.size)
        assertEquals("123", actual.utbetalinger[0].meldeperiode)
        assertEquals(500u, actual.utbetalinger[0].sats)
        assertEquals(500u, actual.utbetalinger[0].utbetaltBeløp)
        assertEquals(4.feb, actual.utbetalinger[0].dato)
    }
}

