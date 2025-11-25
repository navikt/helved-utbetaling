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
import libs.utils.secureLog
import models.notFound
import models.forbidden
import models.conflict
import models.badRequest
import libs.ws.*
import simulering.models.rest.rest
import simulering.models.soap.soap
import simulering.models.soap.soap.Beregning
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

    suspend fun simuler(request: SimulerBeregningRequest): rest.SimuleringResponse {
        val xml = xmlMapper.writeValueAsString(request).replace(Regex("ns\\d="), "xmlns:$0")
        val response = soap.call(SimulerAction.BEREGNING, xml)
        return (json(response) ?: Beregning.empty(request)).intoDto()
    }

    suspend fun simuler(request: rest.SimuleringRequest): rest.SimuleringResponse {
        val request = SimulerBeregningRequest.from(request)
        val xml = xmlMapper.writeValueAsString(request).replace(Regex("ns\\d="), "xmlns:$0")
        val response = soap.call(SimulerAction.BEREGNING, xml)
        return (json(response) ?: Beregning.empty(request)).intoDto()
    }

    fun json(xml: String): Beregning? {
        try {
            wsLog.debug("Forsøker å deserialisere simulering")
            val wrapper = simulerBeregningResponse(xml)
            return wrapper.response?.simulering
        } catch (e: Throwable) {
            try {
                wsLog.error("Feilet deserialisering av simulering", e)
                fault(xml)
            } catch (e2: Throwable) {
                wsLog.error("Feilet deserialisering av simulering", e2)
                throw e2
            }
        }
    }

    private fun simulerBeregningResponse(xml: String): soap.SimulerBeregningResponse =
        runCatching {
            tryInto<soap.SimuleringResponse>(xml).simulerBeregningResponse
        }.getOrElse {
            wsLog.warn("Feilet deserialisering av SOAP-melding")
            secureLog.warn("Feilet deserialisering av SOAP-melding: ${it.message}", it)
            throw it
        }

    // denne kaster exception oppover i call-stacken
    private fun fault(xml: String): Nothing {
        wsLog.debug("Forsøker å deserialisere fault")
        val fault = tryInto<SoapFault>(xml).fault
        logAndThrow(fault)
    }

    private inline fun <reified T> tryInto(xml: String): T {
        val res = xmlMapper.readValue<SoapResponse<T>>(xml)
        return res.body
    }

    private fun logAndThrow(fault: Fault): Nothing {
        wsLog.debug("Håndterer soap fault")
        secureLog.debug("Håndterer soap fault {}", fault)

        with(fault.faultstring) {
            when {
                contains("Personen finnes ikke") -> notFound(this)
                contains("ugyldig") -> badRequest(this)
                contains("Må ha nytt vedtak når Sats/Type/Vedtak-fom endres") -> badRequest(this)
                contains("simulerBeregningFeilUnderBehandling") -> resolveBehandlingFault(fault)
                contains("Conversion to SOAP failed") -> resolveSoapConversionFailure(fault)
                else -> soapError(fault)
            }
        }
    }

    private fun resolveSoapConversionFailure(fault: Fault): Nothing {
        val detail = fault.detail
        val cicsFault = detail["CICSFault"]?.toString() ?: soapError(fault)

        if (cicsFault.contains("DFHPI1008")) {
            forbidden(
                """
                ConsumerId (service-user) er ikke gyldig for simuleringstjenesten.
                Det kan ha vært datalast i Oppdragsystemet. 
                Kontakt oss eller PO Utbetaling.
                """
            )
        } else {
            soapError(fault)
        }
    }

    private fun resolveBehandlingFault(fault: Fault): Nothing {
        val detail = fault.detail
        val feilUnderBehandling = detail["simulerBeregningFeilUnderBehandling"] as? Map<*, *> ?: soapError(fault)
        val errorMessage = feilUnderBehandling["errorMessage"] as? String ?: soapError(fault)
        with(errorMessage) {
            when {
                contains("OPPDRAGET/FAGSYSTEM-ID finnes ikke fra før") -> notFound("SakId ikke funnet")
                contains("Oppdraget finnes fra før") -> conflict("Utbetaling med SakId/BehandlingId finnes fra før")
                contains("Referert vedtak/linje ikke funnet") -> notFound("Endret utbetalingsperiode refererer ikke til en eksisterende utbetalingsperiode")
                contains("Navn på person ikke funnet i PDL") -> notFound("Navn på person ikke funnet i PDL")
                contains("Personen finnes ikke i PDL") -> notFound("Personen finnes ikke i PDL")
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
        .setDefaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_EMPTY, JsonInclude.Include.NON_NULL))
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(
            JavaTimeModule()
                .addDeserializer(
                    LocalDateTime::class.java,
                    LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"))
                )
        )
