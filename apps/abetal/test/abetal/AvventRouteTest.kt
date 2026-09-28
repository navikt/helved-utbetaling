package abetal

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import models.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.*

class AvventRouteTest {
    private val httpClient = TestRuntime.ktor.httpClient

    @Test
    fun `feilregistrerer avvent på transaksjonsnøkkel`() = runBlocking {
        val key = "aap-transaksjon-${UUID.randomUUID()}"
        val request = request(key)

        val response = httpClient.post("/utbetalinger/avvent") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val producer = TestRuntime.kafka.getProducer(Topics.oppdrag)
        val oppdrag = producer.history().single { (recordKey, _) -> recordKey == key }.second
        assertEquals("ENDR", oppdrag.oppdrag110.kodeEndring)
        assertEquals("AAP", oppdrag.oppdrag110.kodeFagomraade)
        assertEquals(request.sakId, oppdrag.oppdrag110.fagsystemId)
        assertEquals(request.personident, oppdrag.oppdrag110.oppdragGjelderId)
        assertEquals(request.saksbehandlerId, oppdrag.oppdrag110.saksbehId)
        assertTrue(oppdrag.oppdrag110.oppdragsLinje150s.isEmpty())
        assertTrue(oppdrag.oppdrag110.oppdragsEnhet120s.isEmpty())
        assertNull(oppdrag.oppdrag110.avstemming115)
        assertEquals("J", oppdrag.oppdrag110.avvent118.feilreg)
        assertEquals(Årsak.AVVENT_AVREGNING.kode, oppdrag.oppdrag110.avvent118.kodeArsak)

        val headers = producer.historyWithHeaders().single { (recordKey, _, _) -> recordKey == key }.third
        assertEquals("abetal-avvent", headers["source"])
    }

    @Test
    fun `avviser feilregistrering uten transaksjonsnøkkel`() = runBlocking {
        val response = httpClient.post("/utbetalinger/avvent") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(request(""))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("key må være satt", response.body<ApiError>().msg)
    }

    @Test
    fun `avviser avvent som ikke er feilregistrert`() = runBlocking {
        val response = httpClient.post("/utbetalinger/avvent") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(request("aap-transaksjon-${UUID.randomUUID()}", feilregistrering = false))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("feilregistrering må være satt til true", response.body<ApiError>().msg)
    }

    @Test
    fun `avviser avvent uten overføringsdato`() = runBlocking {
        val response = httpClient.post("/utbetalinger/avvent") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(request("aap-transaksjon-${UUID.randomUUID()}", overføres = null))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("overføres må være satt", response.body<ApiError>().msg)
    }

    @Test
    fun `krever autentisering for feilregistrering av avvent`() = runBlocking {
        val response = httpClient.post("/utbetalinger/avvent") {
            contentType(ContentType.Application.Json)
            setBody(request("aap-transaksjon-${UUID.randomUUID()}"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    private fun request(
        key: String,
        feilregistrering: Boolean = true,
        overføres: LocalDate? = LocalDate.of(2026, 1, 2),
    ) = FeilregisterAvvent.Request(
        key = key,
        stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
        sakId = "12345",
        personident = "12345678910",
        saksbehandlerId = "Z999999",
        avvent = Avvent(
            fom = LocalDate.of(2026, 2, 1),
            tom = LocalDate.of(2026, 2, 28),
            overføres = overføres,
            årsak = Årsak.AVVENT_AVREGNING,
            feilregistrering = feilregistrering,
        ),
    )
}
