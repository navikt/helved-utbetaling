package simulering

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import libs.utils.Resource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ErrorHandlingTest {
    @Test
    fun `svarer med 400 Bad Request ved feil på request body`() {
        TestRuntime().use { runtime ->
            testApplication {
                application {
                    simulering(config = runtime.config)
                }

                val http = createClient {
                    install(ContentNegotiation) {
                        jackson {
                            registerModule(JavaTimeModule())
                            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        }
                    }
                }

                runtime.soapRespondWith(
                    Resource.read("/soap-fault.xml")
                        .replace("\$errorCode", "lol dummy 123")
                        .replace("\$errorMessage", "Fødselsnummeret er ugyldig")
                )

                val res = http.post("/simulering") {
                    contentType(ContentType.Application.Json)
                    setBody(enSimuleringRequestBody())
                }

                assertEquals(HttpStatusCode.BadRequest, res.status)
            }
        }
    }

    @Test
    fun `can resolve cics error`() {
        TestRuntime().use { runtime ->
            testApplication {
                application {
                    simulering(config = runtime.config)
                }

                val http = createClient {
                    install(ContentNegotiation) {
                        jackson {
                            registerModule(JavaTimeModule())
                            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        }
                    }
                }

                runtime.soapRespondWith(xmlCicsFeil)

                val response = http.post("/simulering") {
                    contentType(ContentType.Application.Json)
                    setBody(enSimuleringRequestBody())
                }

                assertEquals(HttpStatusCode.InternalServerError, response.status)
            }
        }
    }
}

enum class SoapFaultCode {
    VersionMismatch,
    MustUnderstand,
    Client,
    Server
}

private val serverFault = """
<soap:Fault xmlns="">
    <faultcode>SOAP-ENV:Server</faultcode>
    <faultstring>Conversion from SOAP failed</faultstring>
</soap:Fault>
""".trimIndent()


private val clientFault = """
<soap:Fault xmlns="">
    <faultcode>SOAP-ENV:Server</faultcode>
    <faultstring>Conversion from SOAP failed</faultstring>
</soap:Fault>
""".trimIndent()


private val versionMismatchFault = """
<soap:Fault xmlns="">
    <faultcode>SOAP-ENV:Server</faultcode>
    <faultstring>Conversion from SOAP failed</faultstring>
</soap:Fault>
""".trimIndent()

private val mustUnderstandFault = """
<soap:Fault xmlns="">
    <faultcode>SOAP-ENV:Server</faultcode>
    <faultstring>Conversion from SOAP failed</faultstring>
</soap:Fault>
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

private val xmlSimulerBeregningFeilUnderBehandling = """
    <S:Fault xmlns="">
    <faultcode>Soap:Client</faultcode>
    <faultstring>simulerBeregningFeilUnderBehandling                                             </faultstring>
    <detail>
        <sf:simulerBeregningFeilUnderBehandling xmlns:sf="http://nav.no/system/os/tjenester/oppdragService">
            <errorMessage>UTBETALES-TIL-ID er ikke utfylt</errorMessage>
            <errorSource>K231BB50 section: CA10-KON</errorSource>
            <rootCause>Kode BB50018F - SQL      - MQ</rootCause>
            <dateTimeStamp>2024-01-14T09:41:29</dateTimeStamp>
        </sf:simulerBeregningFeilUnderBehandling>
    </detail>
</S:Fault>
""".trimIndent()
