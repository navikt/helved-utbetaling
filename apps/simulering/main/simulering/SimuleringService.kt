@file:Suppress("NAME_SHADOWING")

package simulering

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
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.utils.appLog
import libs.utils.secureLog
import libs.ws.*
import simulering.models.rest.rest
import simulering.models.soap.soap
import simulering.models.soap.soap.SimulerBeregningRequest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    private val sts = StsClient(config.simulering.sts, http, proxyAuth = ::getAzureToken)
    private val soap = SoapClient(config.simulering, sts, http, proxyAuth = ::getAzureToken)

    suspend fun simuler(request: rest.SimuleringRequest): rest.SimuleringResponse {
        val request = SimulerBeregningRequest.from(request)
        val xml = xmlMapper.writeValueAsString(request).replace(Regex("ns\\d="), "xmlns:$0")
        val response = soap.call(SimulerAction.BEREGNING, xml)
        return json(response).intoDto()
    }

    fun json(xml: String): soap.Beregning {
        try {
            secureLog.info("Forsøker å deserialisere simulering")
            val wrapper = simulerBeregningResponse(xml)
            return wrapper.response.simulering
        } catch (e: Throwable) {
            secureLog.info("feilet deserializering av simulering", e)
            fault(xml)
        }
    }

    private fun simulerBeregningResponse(xml: String): soap.SimulerBeregningResponse =
        runCatching {
            tryInto<soap.SimuleringResponse>(xml).simulerBeregningResponse
        }.getOrElse {
            appLog.error("Failed to deserialize soap message: ${it.message}")
            secureLog.error("Failed to deserialize soap message: ${it.message}", it)
            throw it
        }

    // denne kaster exception oppover i call-stacken
    private fun fault(xml: String): Nothing {
        try {
            secureLog.info("Forsøker å deserialisere fault")
            val fault = tryInto<SoapFault>(xml).fault
            logAndThrow(fault)
        } catch (e: Throwable) {
            appLog.error("feilet deserializering av fault")
            secureLog.error("feilet deserializering av fault", e)
            throw e
        }
    }

    private inline fun <reified T> tryInto(xml: String): T {
        val res = xmlMapper.readValue<SoapResponse<T>>(xml)
        return res.body
    }

    private fun logAndThrow(fault: Fault): Nothing {
        secureLog.info("Håndterer soap fault {}", fault)

        with(fault.faultstring) {
            when {
                contains("Personen finnes ikke") -> throw IkkeFunnet(this)
                contains("ugyldig") -> throw RequestErUgyldigException(this)
                contains("simulerBeregningFeilUnderBehandling") -> resolveBehandlingFault(fault)
                else -> soapError(fault)
            }
        }
    }

    private fun resolveBehandlingFault(fault: Fault): Nothing {
        val detail = fault.detail?.let(xmlMapper::readTree) ?: soapError(fault)

        with(detail["errorMessage"].textValue()) {
            when {
                this == null -> soapError(fault)
                contains("OPPDRAGET/FAGSYSTEM-ID finnes ikke fra før") -> throw IkkeFunnet("SakId ikke funnet")
                contains("Referert vedtak/linje ikke funnet") -> throw IkkeFunnet("Endret utbetalingsperiode refererer ikke til en eksisterende utbetalingsperiode")
                else -> soapError(fault)
            }
        }
    }

    private suspend fun getAzureToken(): String {
        return "Bearer ${azure.getClientCredentialsToken(config.proxy.scope).access_token}"
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

class IkkeFunnet(feilmelding: String) : RuntimeException(feilmelding)
class RequestErUgyldigException(feilmelding: String) : RuntimeException(feilmelding)
class OppdragErStengtException : RuntimeException("Oppdrag/UR er stengt")
