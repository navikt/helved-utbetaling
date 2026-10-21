package abetal

import models.*
import libs.kotlinx.KotlinxJson
import org.http4k.core.*
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.*

class AvventRouteTest {
    private val handler = TestRuntime.app.handler

    @Test
    fun `avviser ugyldige token før meldingen behandles`() {
        val azure = TestRuntime.azure.config
        val tokens = listOf(
            "invalid",
            libs.auth.JwkGenerator("wrong-issuer", azure.clientId).generate(),
            libs.auth.JwkGenerator(azure.issuer, "wrong-audience").generate()
        )
        for (token in tokens) {
            val response = handler(Request(Method.POST, "/utbetalinger/avvent")
                .header("Authorization", "Bearer $token")
                .body("{"))
            assertEquals(Status.UNAUTHORIZED, response.status)
        }
    }

    @Test
    fun `ugyldig JSON gir strukturert feilrespons`() {
        for (body in listOf("{", "{}")) {
            val response = handler(Request(Method.POST, "/utbetalinger/avvent")
                .header("Authorization", "Bearer ${TestRuntime.azure.generateToken()}")
                .header("Content-Type", "application/json")
                .body(body))
            assertEquals(Status.BAD_REQUEST, response.status)
            assertEquals(400, KotlinxJson.decodeFromString<ApiError>(response.bodyString()).statusCode)
        }
    }

    @Test
    fun `feilregistrerer avvent på transaksjonsnøkkel`() {
        val key = "aap-transaksjon-${UUID.randomUUID()}"
        val request = request(key)

        val response = handler(
            Request(Method.POST, "/utbetalinger/avvent")
                .header("Authorization", "Bearer ${TestRuntime.azure.generateToken()}")
                .header("Content-Type", "application/json")
                .body(KotlinxJson.encodeToString(request))
        )

        assertEquals(Status.CREATED, response.status)
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
    fun `avviser feilregistrering uten transaksjonsnøkkel`() {
        val response = handler(
            Request(Method.POST, "/utbetalinger/avvent")
                .header("Authorization", "Bearer ${TestRuntime.azure.generateToken()}")
                .header("Content-Type", "application/json")
                .body(KotlinxJson.encodeToString(request("")))
        )

        assertEquals(Status.BAD_REQUEST, response.status)
        assertEquals("key må være satt", KotlinxJson.decodeFromString<ApiError>(response.bodyString()).msg)
    }

    @Test
    fun `avviser avvent som ikke er feilregistrert`() {
        val response = handler(
            Request(Method.POST, "/utbetalinger/avvent")
                .header("Authorization", "Bearer ${TestRuntime.azure.generateToken()}")
                .header("Content-Type", "application/json")
                .body(KotlinxJson.encodeToString(request("aap-transaksjon-${UUID.randomUUID()}", feilregistrering = false)))
        )

        assertEquals(Status.BAD_REQUEST, response.status)
        assertEquals("feilregistrering må være satt til true", KotlinxJson.decodeFromString<ApiError>(response.bodyString()).msg)
    }

    @Test
    fun `avviser avvent uten overføringsdato`() {
        val response = handler(
            Request(Method.POST, "/utbetalinger/avvent")
                .header("Authorization", "Bearer ${TestRuntime.azure.generateToken()}")
                .header("Content-Type", "application/json")
                .body(KotlinxJson.encodeToString(request("aap-transaksjon-${UUID.randomUUID()}", overføres = null)))
        )

        assertEquals(Status.BAD_REQUEST, response.status)
        assertEquals("overføres må være satt", KotlinxJson.decodeFromString<ApiError>(response.bodyString()).msg)
    }

    @Test
    fun `krever autentisering for feilregistrering av avvent`() {
        val response = handler(
            Request(Method.POST, "/utbetalinger/avvent")
                .header("Content-Type", "application/json")
                .body(KotlinxJson.encodeToString(request("aap-transaksjon-${UUID.randomUUID()}")))
        )

        assertEquals(Status.UNAUTHORIZED, response.status)
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
