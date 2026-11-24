package simulering

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import libs.kafka.KafkaProducerFake
import libs.utils.Resource
import models.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Isolated
class KafkaSimuleringTest {
    @BeforeEach
    fun setup() {
        TestRuntime.app
        TestRuntime.reset()
    }

    @Test
    fun `simulering for TS produserer v1 resultat til dryrun-ts topic`() {
        TestRuntime.soapRespondWith(Resource.read("/simuler-jaxb-response.xml"))

        val result = simulate("test-key", "TILLST", TestRuntime.dryrunTs)

        assertTrue(result is v1.Simulering)
        assertEquals(1, TestRuntime.receivedSoapRequests.size)
        assertTrue(TestRuntime.receivedSoapRequests.single().contains("TILLST"))
    }

    @Test
    fun `simulering for AAP produserer v2 resultat til dryrun-aap topic`() {
        TestRuntime.soapRespondWith(Resource.read("/simuler-jaxb-response.xml"))

        assertTrue(simulate("aap-key", "AAP", TestRuntime.dryrunAap) is v2.Simulering)
    }

    @Test
    fun `SOAP fault produserer Info med UGYLDIG_REQUEST`() {
        TestRuntime.soapRespondWith(soapFault("""
            <faultcode>soap:Server</faultcode>
            <faultstring>simulerBeregningFeilUnderBehandling</faultstring>
            <detail>
                <sf:simulerBeregningFeilUnderBehandling xmlns:sf="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
                    <errorMessage>Personen finnes ikke</errorMessage>
                </sf:simulerBeregningFeilUnderBehandling>
            </detail>
        """.trimIndent()))

        val result = simulate("dp-key", "DP", TestRuntime.dryrunDp)

        assertTrue(result is Info)
        assertEquals(Info.Status.UGYLDIG_REQUEST, result.status)
    }

    @Test
    fun `tom SOAP-respons produserer Info OkUtenEndring for TS`() {
        TestRuntime.soapRespondWith("""
            <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt"></simulerBeregningResponse>
        """.trimIndent())

        val result = simulate("ts-key", "TILLST", TestRuntime.dryrunTs)

        assertTrue(result is Info)
        assertEquals(Info.Status.OK_UTEN_ENDRING, result.status)
    }

    @Test
    fun `simuler sak 200001495 produserer korrekt v1 resultat`() {
        TestRuntime.soapRespondWith(Resource.read("/simuler-ts-200001495.xml"))
        assertTrue(simulate("ts-key", "TILLST", TestRuntime.dryrunTs) is v1.Simulering)
    }

    @Test
    fun `simuler sak 200001495 produserer korrekt v2 resultat`() {
        TestRuntime.soapRespondWith(Resource.read("/simuler-ts-200001495.xml"))
        assertTrue(simulate("aap-key", "AAP", TestRuntime.dryrunAap) is v2.Simulering)
    }

    @Test
    fun `simuler sak 4819 produserer korrekt v1 resultat`() {
        TestRuntime.soapRespondWith(Resource.read("/simuler-ts-4819.xml"))
        assertTrue(simulate("ts-key", "TILLST", TestRuntime.dryrunTs) is v1.Simulering)
    }

    @Test
    fun `simuler sak 4819 produserer korrekt v2 resultat`() {
        TestRuntime.soapRespondWith(Resource.read("/simuler-ts-4819.xml"))
        assertTrue(simulate("aap-key", "AAP", TestRuntime.dryrunAap) is v2.Simulering)
    }

    @Test
    fun `full dryrun round-trip via kafka topology`() {
        TestRuntime.soapRespondWith(Resource.read("/simuler-jaxb-response.xml"))
        val headers = mapOf("x-correlation-id" to "header-value")

        val result = simulate("round-trip-key", "AAP", TestRuntime.dryrunAap, headers)

        assertEquals(headers, TestRuntime.dryrunAap.historyWithHeaders().single().third)
        // StreamsMock does not forward producer records into its input topics.
        TestRuntime.kafka.testInputTopic(Topics.dryrunAap).produce("round-trip-key") { result }
        assertEquals(result, TestRuntime.kafka.getStore<String, Simulering>(Stores.dryrunAap).getOrNull("round-trip-key"))
    }

    @Test
    fun `worker retries authentication with a fresh STS token`() {
        TestRuntime.soapRespondWithSequence(
            soapFault("""
                <faultcode>soap:Server</faultcode>
                <faultstring>FailedAuthentication</faultstring>
            """.trimIndent()),
            Resource.read("/simuler-jaxb-response.xml")
        )

        assertTrue(simulate("auth-key", "AAP", TestRuntime.dryrunAap) is v2.Simulering)
        assertEquals(2, TestRuntime.receivedSoapRequests.size)
        assertEquals(1, TestRuntime.invalidateCount)
    }

    private fun simulate(
        key: String,
        fagområde: String,
        producer: KafkaProducerFake<String, Simulering>,
        headers: Map<String, String> = emptyMap()
    ): Simulering {
        TestRuntime.kafka.testInputTopic(Topics.simuleringer).produce(key, headers) { simulering(fagområde = fagområde) }
        TestRuntime.kafka.advanceWallClockTime(6.seconds)
        runBlocking {
            withTimeout(5.seconds) {
                while (producer.history().none { it.first == key }) delay(10.milliseconds)
            }
        }
        val (actualKey, result) = producer.history().single()
        assertEquals(key, actualKey)
        TestRuntime.awaitShadow(key)
        return result
    }

    private fun soapFault(body: String): String = """
        <SOAP-ENV:Fault xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
            $body
        </SOAP-ENV:Fault>
    """.trimIndent()
}
