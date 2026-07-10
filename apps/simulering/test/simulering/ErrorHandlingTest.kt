package simulering

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import libs.utils.Resource
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.lens.BiDiBodyLens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import simulering.v1.rest

class ErrorHandlingTest {

    private val requestLens: BiDiBodyLens<rest.SimuleringRequest> = KotlinxJson.autoBody<rest.SimuleringRequest>().toLens()

    @BeforeEach
    fun setup() {
        TestRuntime.reset()
    }

    @Test
    fun `svarer med 400 Bad Request ved feil på request body`() {
        TestRuntime.soapRespondWith(
            Resource.read("/soap-fault.xml")
                .replace("\$errorCode", "lol dummy 123")
                .replace("\$errorMessage", "Fødselsnummeret er ugyldig")
        )

        val response = TestRuntime.app(
            Request(Method.POST, "/simuler/legacy")
                .header("Content-Type", "application/json")
                .with(requestLens of enSimuleringRequestBody())
        )

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `can resolve cics error`() {
        TestRuntime.soapRespondWith(xmlCicsFeil)

        val response = TestRuntime.app(
            Request(Method.POST, "/simuler/legacy")
                .header("Content-Type", "application/json")
                .with(requestLens of enSimuleringRequestBody())
        )

        assertEquals(Status.INTERNAL_SERVER_ERROR, response.status)
    }

    @Test
    fun `fornyer STS-token og prøver på nytt ved FailedAuthentication`() {
        TestRuntime.soapRespondWithSequence(
            failedAuthenticationFault,
            Resource.read("/simuler-body-response.xml"),
        )

        val response = TestRuntime.app(
            Request(Method.POST, "/simuler/legacy")
                .header("Content-Type", "application/json")
                .with(requestLens of enSimuleringRequestBody())
        )

        assertEquals(Status.OK, response.status)
        assertEquals(2, TestRuntime.receivedSoapRequests.size)
        assertEquals(1, TestRuntime.invalidateCount)
    }

    @Test
    fun `svarer med 409 Conflict naar Oppdraget finnes fra foer`() {
        TestRuntime.soapRespondWith(oppdragFinnesFraFoerFault)

        val response = TestRuntime.app(
            Request(Method.POST, "/simuler/legacy")
                .header("Content-Type", "application/json")
                .with(requestLens of enSimuleringRequestBody())
        )

        assertEquals(Status.CONFLICT, response.status)
    }

    @Test
    fun `logger ikke ERROR ved kjent fault`() {
        val wsLogger = LoggerFactory.getLogger("ws") as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        wsLogger.addAppender(appender)
        try {
            TestRuntime.soapRespondWith(oppdragFinnesFraFoerFault)

            val response = TestRuntime.app(
                Request(Method.POST, "/simuler/legacy")
                    .header("Content-Type", "application/json")
                    .with(requestLens of enSimuleringRequestBody())
            )

            assertEquals(Status.CONFLICT, response.status)
            val errors = appender.list.filter { it.level == Level.ERROR }
            assertTrue(
                errors.isEmpty(),
                "ws-loggeren skal ikke logge ERROR for kjent fault, men logget: ${errors.map { it.formattedMessage }}"
            )
        } finally {
            wsLogger.detachAppender(appender)
        }
    }

    @Test
    fun `svarer med 502 Bad Gateway ved ukjent SOAP-svar`() {
        TestRuntime.soapRespondWith("<noise>uventet</noise>")

        val response = TestRuntime.app(
            Request(Method.POST, "/simuler/legacy")
                .header("Content-Type", "application/json")
                .with(requestLens of enSimuleringRequestBody())
        )

        assertEquals(Status.BAD_GATEWAY, response.status)
    }
}

private val failedAuthenticationFault = """
<soap:Fault xmlns="">
    <faultcode>wsse:FailedAuthentication</faultcode>
    <faultstring>A security token could not be authenticated</faultstring>
</soap:Fault>
""".trimIndent()

private val oppdragFinnesFraFoerFault = """
<SOAP-ENV:Fault xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns="">
    <faultcode>SOAP-ENV:Client</faultcode>
    <faultstring>simulerBeregningFeilUnderBehandling                                             </faultstring>
    <detail>
        <sf:simulerBeregningFeilUnderBehandling xmlns:sf="http://nav.no/system/os/tjenester/oppdragService">
            <errorMessage>Oppdraget finnes fra før</errorMessage>
            <errorSource>K231BB10 section: CA10-INP</errorSource>
            <rootCause>Kode B110008F - SQL      - MQ</rootCause>
            <dateTimeStamp>2026-05-21T08:44:39</dateTimeStamp>
        </sf:simulerBeregningFeilUnderBehandling>
    </detail>
</SOAP-ENV:Fault>
""".trimIndent()

private val xmlCicsFeil = """
<soap:Fault xmlns="">
    <faultcode>SOAP-ENV:Server</faultcode>
    <faultstring>Conversion from SOAP failed</faultstring>
    <detail>
        <CICSFault xmlns="http://www.ibm.com/software/htp/cics/WSFault">RUTINE1 17/01/2024 08:55:44 CICS01
            ERR01 1337 XML to data transformation failed. A conversion error (OUTPUT_OVERFLOW) occurred when
            converting field maksDato for WEBSERVICE simulerFpServiceWSBinding.
        </CICSFault>
    </detail>
</soap:Fault>
""".trimIndent()
