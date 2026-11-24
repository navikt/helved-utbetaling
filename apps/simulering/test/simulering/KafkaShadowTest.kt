package simulering

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import libs.utils.Resource
import models.*
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Isolated
class KafkaShadowTest {
    @BeforeEach
    fun setup() {
        TestRuntime.app
        TestRuntime.reset()
        TestRuntime.soapRespondWith(Resource.read("/simuler-jaxb-response.xml"))
    }

    @Test
    fun `Kafka skyggekall logger REST request response og begge v1 resultater sikkert`() {
        verifyComparison("TILLST", Fagsystem.TILLEGGSSTØNADER, "v1")
    }

    @Test
    fun `Kafka skyggekall logger REST request response og begge v2 resultater sikkert`() {
        verifyComparison("AAP", Fagsystem.AAP, "v2")
    }

    @Test
    fun `tiltakspenger sammenlignes med v1`() {
        verifyComparison("TILTPENG", Fagsystem.TILTAKSPENGER, "v1")
    }

    private fun verifyComparison(fagområde: String, fagsystem: Fagsystem, version: String) {
        TestRuntime.soapRespondWith(Resource.read("/shadow-soap-response.xml").replace("FAGOMRADE", fagområde))
        val restBody = Resource.read("/sokos-response.json").replace("FAGOMRADE", fagområde)
        TestRuntime.restHandler = { Response(Status.OK).body(restBody) }

        val authoritative = produce("compare-key", fagområde, fagsystem)
        TestRuntime.awaitShadow("compare-key")

        val request = TestRuntime.receivedRestRequests.single()
        assertEquals(Method.POST, request.method)
        assertEquals("http://sokos.test/api/osSimulerBeregningApi/simulerBeregning", request.uri.toString())
        assertEquals("Bearer shadow-test-token", request.header("Authorization"))
        assertEquals("application/json", request.header("Content-Type"))
        assertEquals("application/json", request.header("Accept"))
        val input = Json.parseToJsonElement(request.bodyString()).jsonObject
            .getValue("OsSimulerBeregningOperation").jsonObject.getValue("ZS1ICont").jsonObject.getValue("requestSimulering").jsonObject
        assertEquals(fagområde, input.getValue("oppdrag").jsonObject.getValue("kodeFagomraade").jsonPrimitive.content)
        assertEquals("12345678910", input.getValue("oppdrag").jsonObject.getValue("oppdragGjelderId").jsonPrimitive.content)
        val line = input.getValue("oppdragslinje").jsonArray.single().jsonObject
        assertEquals(false, line.getValue("brukKjoreplan").jsonPrimitive.boolean)
        assertEquals(700, line.getValue("sats").jsonPrimitive.int)
        assertFalse(line.containsKey("datoStatusFom"), "Null fields must be omitted")

        val events = TestRuntime.shadowEvents("compare-key")
        assertEquals(1, events.map { it.getValue("comparisonId") }.distinct().size)
        assertTrue(events.all { it.getValue("modelVersion").jsonPrimitive.content == version })
        assertEquals(input, events.single { it["restRequest"] != null }.getValue("restRequest").jsonObject
            .getValue("OsSimulerBeregningOperation").jsonObject.getValue("ZS1ICont").jsonObject.getValue("requestSimulering"))
        assertEquals(restBody, events.single { it["restResponse"] != null }.getValue("restResponse").jsonPrimitive.content)
        val comparison = events.single { it["restMapped"] != null }
        assertEquals(libs.kotlinx.KotlinxJson.encodeToJsonElement(authoritative), comparison["soapMapped"])
        assertEquals(comparison["soapMapped"], comparison["restMapped"])
        assertEquals(true, comparison.getValue("equal").jsonPrimitive.boolean)
        assertFalse(TestRuntime.secureEvents.any { it.formattedMessage.contains("shadow-test-token") })
    }

    @Test
    fun `HTTP feil og ugyldig JSON endrer ikke SOAP resultatet`() {
        val failures: List<org.http4k.core.HttpHandler> = listOf(
            { Response(Status.BAD_GATEWAY).body("sensitive downstream failure") },
            { Response(Status.OK).body("not JSON with personal data") }
        )
        failures.forEachIndexed { index, handler ->
            TestRuntime.restHandler = handler
            val result = produce("failure-$index", "AAP", Fagsystem.AAP)
            TestRuntime.awaitShadow("failure-$index")
            assertTrue(result is v2.Simulering)
            assertTrue(TestRuntime.shadowEvents("failure-$index").any { it["event"]?.jsonPrimitive?.content == "simulering_shadow_failed" })
        }
    }

    @Test
    fun `CLIENT_TIMEOUT logges sikkert uten retry eller endring av SOAP resultatet`() {
        TestRuntime.restHandler = { Response(Status.CLIENT_TIMEOUT) }

        val result = produce("timeout-key", "AAP", Fagsystem.AAP)
        TestRuntime.awaitShadow("timeout-key")

        assertIs<v2.Simulering>(result)
        assertTrue(result.perioder.isNotEmpty())
        assertEquals(1, TestRuntime.dryrunAap.history().size)
        assertEquals(1, TestRuntime.receivedRestRequests.size)
        val events = TestRuntime.shadowEvents("timeout-key")
        val response = events.single { it["event"]?.jsonPrimitive?.content == "simulering_shadow_rest_response" }
        assertEquals(Status.CLIENT_TIMEOUT.code, response.getValue("httpStatus").jsonPrimitive.int)
        assertEquals("", response.getValue("restResponse").jsonPrimitive.content)
        val failure = events.single { it["event"]?.jsonPrimitive?.content == "simulering_shadow_failed" }
        assertTrue(failure.getValue("error").jsonPrimitive.content.contains("HTTP ${Status.CLIENT_TIMEOUT.code}"))
        val soap = events.single { it["soapMapped"] != null }
        assertEquals(libs.kotlinx.KotlinxJson.encodeToJsonElement<Simulering>(result), soap["soapMapped"])
        assertTrue(events.none { it["restMapped"] != null })
    }

    @Test
    fun `SOAP publiseres mens REST venter`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        TestRuntime.restHandler = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            Response(Status.OK).body(Resource.read("/sokos-response.json").replace("FAGOMRADE", "AAP"))
        }
        try {
            val result = produce("slow-key", "AAP", Fagsystem.AAP)
            assertTrue(result is v2.Simulering)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertFalse(TestRuntime.shadowEvents("slow-key").any { it["restMapped"] != null })
        } finally {
            release.countDown()
            TestRuntime.awaitShadow("slow-key")
        }
    }

    @Test
    fun `legacy REST utløser ikke skyggekall`() {
        TestRuntime.soapRespondWith(Resource.read("/simuler-body-response.xml"))
        val lens = KotlinxJson.autoBody<simulering.v1.rest.SimuleringRequest>().toLens()
        val response = TestRuntime.app(Request(Method.POST, "/simuler/legacy").with(lens of enSimuleringRequestBody()))
        assertEquals(Status.OK, response.status)
        assertEquals(1, TestRuntime.receivedSoapRequests.size)
        assertTrue(TestRuntime.receivedRestRequests.isEmpty())
        assertTrue(TestRuntime.secureEvents.none { it.formattedMessage.contains("simulering_shadow_") })
    }

    private fun produce(key: String, fagområde: String, fagsystem: Fagsystem): Simulering {
        TestRuntime.kafka.testInputTopic(Topics.simuleringer).produce(key) { simulering(fagområde = fagområde) }
        TestRuntime.kafka.advanceWallClockTime(6.seconds)
        val producer = when (fagsystem) {
            Fagsystem.TILLEGGSSTØNADER -> TestRuntime.dryrunTs
            Fagsystem.TILTAKSPENGER -> TestRuntime.dryrunTp
            else -> TestRuntime.dryrunAap
        }
        return runBlocking {
            withTimeout(5.seconds) {
                while (producer.history().none { it.first == key }) delay(10.milliseconds)
                producer.history().single { it.first == key }.second
            }
        }
    }
}
