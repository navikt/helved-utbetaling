@file:Suppress("NAME_SHADOWING")

package simulering

import com.ctc.wstx.exc.WstxEOFException
import com.ctc.wstx.exc.WstxIOException
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.plugins.logging.*
import jakarta.xml.ws.WebServiceException
import jakarta.xml.ws.soap.SOAPFaultException
import kotlinx.coroutines.runBlocking
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.utils.appLog
import libs.utils.secureLog
import libs.ws.*
import simulering.dto.SimuleringRequestBody
import java.net.SocketException
import java.net.SocketTimeoutException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLException

private object SimulerAction {
    private const val HOST = "http://nav.no"
    private const val PATH = "system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt"
    private const val SERVICE = "simulerFpService"
    const val BEREGNING = "$HOST/$PATH/$SERVICE/simulerBeregning"
    const val SEND_OPPDRAG = "$HOST/$PATH/$SERVICE/sendInnOppdragRequest"
}

class SimuleringService(private val config: Config) {
    private val http = HttpClientFactory.new(LogLevel.ALL)
    private val azure = AzureTokenProvider(config.azure)
    private val sts = StsClient(config.simulering.sts, http, proxyAuth = ::getAzureTokenAsync)
    private val soap = SoapClient(config.simulering, sts, http, proxyAuth = ::getAzureToken)

    suspend fun simuler(request: SimuleringRequestBody): Simulering {
        val request = SimulerBeregning.from(request)
        val xml = xmlMapper.writeValueAsString(request)
        val response = soap.call(SimulerAction.BEREGNING, hardkodet())
        return json(response).intoDto()
    }

    private fun hardkodet(): String {
        return """
           <ns3:simulerBeregningRequest xmlns:ns2="http://nav.no/system/os/entiteter/oppdragSkjema"
                                     xmlns:ns3="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
            <request>
                <oppdrag>
                    <kodeEndring>NY</kodeEndring>
                    <kodeFagomraade>TILLST</kodeFagomraade>
                    <fagsystemId>200000237</fagsystemId>
                    <utbetFrekvens>MND</utbetFrekvens>
                    <oppdragGjelderId>22479409483</oppdragGjelderId>
                    <datoOppdragGjelderFom>1970-01-01</datoOppdragGjelderFom>
                    <saksbehId>Z994230</saksbehId>
                    <ns2:enhet>
                        <typeEnhet>BOS</typeEnhet>
                        <enhet>8020</enhet>
                        <datoEnhetFom>1970-01-01</datoEnhetFom>
                    </ns2:enhet>
                    <oppdragslinje>
                        <kodeEndringLinje>NY</kodeEndringLinje>
                        <delytelseId>200000233#0</delytelseId>
                        <kodeKlassifik>TSTBASISP4-OP</kodeKlassifik>
                        <datoVedtakFom>2024-05-01</datoVedtakFom>
                        <datoVedtakTom>2024-05-01</datoVedtakTom>
                        <sats>700</sats>
                        <fradragTillegg>T</fradragTillegg>
                        <typeSats>DAG</typeSats>
                        <brukKjoreplan>N</brukKjoreplan>
                        <saksbehId>Z994230</saksbehId>
                        <utbetalesTilId>22479409483</utbetalesTilId>
                        <ns2:grad>
                            <typeGrad>UFOR</typeGrad>
                        </ns2:grad>
                        <ns2:attestant>
                            <attestantId>Z994230</attestantId>
                        </ns2:attestant>
                    </oppdragslinje>
                </oppdrag>
                <simuleringsPeriode>
                    <datoSimulerFom>2024-05-01</datoSimulerFom>
                    <datoSimulerTom>2024-05-01</datoSimulerTom>
                </simuleringsPeriode>
            </request>
        </ns3:simulerBeregningRequest>
    </soap:Body>
       """.trimIndent()
    }

    private fun json(xml: String): SimuleringResponse.SimulerBeregningResponse.Response.Beregning {
        try {
            secureLog.info("Forsøker å deserialisere simulering")
            val wrapper = simulerBeregningResponse(xml)
            return wrapper.response.simulering
        } catch (e: Throwable) {
            secureLog.info("feilet deserializering av simulering", e)
            fault(xml)
        }
    }

    private fun simulerBeregningResponse(xml: String): SimuleringResponse.SimulerBeregningResponse = runCatching {
        tryInto<SimuleringResponse>(xml).simulerBeregningResponse
    }.getOrElse {
        throw soapError("Failed to deserialize soap message: ${it.message}", it)
    }

    // denne kaster exception oppover i call-stacken
    private fun fault(xml: String): Nothing {
        try {
            secureLog.info("Forsøker å deserialisere fault")
            throw soapError(tryInto<SoapFault>(xml).fault)
        } catch (e: Throwable) {
            throw when (e) {
                is SoapException -> expand(e)
                else -> {
                    appLog.error("feilet deserializering av fault")
                    secureLog.error("feilet deserializering av fault", e)
                    soapError("Ukjent feil ved simulering: ${e.message}", e)
                }
            }
        }
    }

    private inline fun <reified T> tryInto(xml: String): T {
        val res = xmlMapper.readValue<SoapResponse<T>>(xml)
        return res.body
    }

    private fun expand(e: SoapException): RuntimeException {
        secureLog.info("expands soapfault", e)
        return with(e.msg) {
            when {
                contains("Personen finnes ikke") -> PersonFinnesIkkeException(this)
                contains("ugyldig") -> RequestErUgyldigException(this)
                else -> e
            }
        }
    }

    private fun soapFault(ex: SOAPFaultException) {
        logSoapFaultException(ex)

        when (ex.cause) {
            is WstxEOFException, is WstxIOException -> throw OppdragErStengtException()
            else -> throw ex
        }
    }

    private fun webserviceFault(ex: WebServiceException) {
        when (ex.cause) {
            is SSLException, is SocketException, is SocketTimeoutException -> throw OppdragErStengtException()
            else -> throw ex
        }
    }

    private suspend fun getAzureTokenAsync(): String {
        return "Bearer ${azure.getClientCredentialsToken(config.proxy.scope).access_token}"
    }

    private fun getAzureToken(): String {
        return runBlocking {
            "Bearer ${azure.getClientCredentialsToken(config.proxy.scope).access_token}"
        }
    }
}

private val xmlMapper: ObjectMapper =
    XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) })
        .registerKotlinModule()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(
            JavaTimeModule()
                .addDeserializer(
                    LocalDateTime::class.java,
                    LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("YYYY-MM-dd'T'HH:mm:ssZ"))
                )
        )

class PersonFinnesIkkeException(feilmelding: String) : RuntimeException(feilmelding)
class RequestErUgyldigException(feilmelding: String) : RuntimeException(feilmelding)
class OppdragErStengtException : RuntimeException("Oppdrag/UR er stengt")

private fun logSoapFaultException(e: SOAPFaultException) {
    val details = e.fault.detail
        ?.detailEntries
        ?.asSequence()
        ?.mapNotNull { it.textContent }
        ?.joinToString(",")

    secureLog.error(
        """
            SOAPFaultException -
                faultCode=${e.fault.faultCode}
                faultString=${e.fault.faultString}
                details=$details
        """.trimIndent()
    )
}
