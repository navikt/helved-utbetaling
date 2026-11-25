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
    fun `Kafka skyggekall sender REST request og beholder v1 SOAP resultat`() {
        verifyShadowRequest("TILLST", Fagsystem.TILLEGGSSTØNADER)
    }

    @Test
    fun `Kafka skyggekall sender REST request og beholder v2 SOAP resultat`() {
        verifyShadowRequest("AAP", Fagsystem.AAP)
    }

    @Test
    fun `tiltakspenger beholder v1 SOAP resultat med REST skyggekall`() {
        verifyShadowRequest("TILTPENG", Fagsystem.TILTAKSPENGER)
    }

    private fun verifyShadowRequest(fagområde: String, fagsystem: Fagsystem) {
        TestRuntime.soapRespondWith(Resource.read("/shadow-soap-response.xml").replace("FAGOMRADE", fagområde))
        val restBody = Resource.read("/sokos-response.json").replace("FAGOMRADE", fagområde)
        TestRuntime.restHandler = { Response(Status.OK).body(restBody) }

        val authoritative = produce("compare-key", fagområde, fagsystem)
        if (fagsystem == Fagsystem.AAP) assertIs<v2.Simulering>(authoritative) else assertIs<v1.Simulering>(authoritative)

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
            assertIs<v2.Simulering>(result)
            assertTrue(result.perioder.isNotEmpty())
            assertEquals(index + 1, TestRuntime.receivedRestRequests.size)
        }
    }

    @Test
    fun `CLIENT_TIMEOUT endrer ikke SOAP resultatet`() {
        TestRuntime.restHandler = { Response(Status.CLIENT_TIMEOUT) }

        val result = produce("timeout-key", "AAP", Fagsystem.AAP)

        assertIs<v2.Simulering>(result)
        assertTrue(result.perioder.isNotEmpty())
        assertEquals(1, TestRuntime.dryrunAap.history().size)
        assertEquals(1, TestRuntime.receivedRestRequests.size)
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
            val result = produce("slow-key", "AAP", Fagsystem.AAP, waitForRest = false)
            assertTrue(result is v2.Simulering)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(1L, release.count)
        } finally {
            release.countDown()
            TestRuntime.awaitRestCalls(1)
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
    }

    private fun produce(key: String, fagområde: String, fagsystem: Fagsystem, waitForRest: Boolean = true): Simulering {
        val expectedRestCalls = TestRuntime.receivedRestRequests.size + 1
        TestRuntime.kafka.testInputTopic(Topics.simuleringer).produce(key) { simulering(fagområde = fagområde) }
        TestRuntime.kafka.advanceWallClockTime(6.seconds)
        val producer = when (fagsystem) {
            Fagsystem.TILLEGGSSTØNADER -> TestRuntime.dryrunTs
            Fagsystem.TILTAKSPENGER -> TestRuntime.dryrunTp
            else -> TestRuntime.dryrunAap
        }
        val result = runBlocking {
            withTimeout(5.seconds) {
                while (producer.history().none { it.first == key }) delay(10.milliseconds)
                producer.history().single { it.first == key }.second
            }
        }
        if (waitForRest) TestRuntime.awaitRestCalls(expectedRestCalls)
        return result
    }
}
