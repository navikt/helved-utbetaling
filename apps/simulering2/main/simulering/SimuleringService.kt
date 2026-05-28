package simulering

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import libs.utils.secureLog
import models.badGateway
import models.badRequest
import models.conflict
import models.forbidden
import models.notFound
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

class SimuleringService(private val soap: Soap, private val sts: Sts) {

    fun simuler(request: SimulerBeregningRequest): rest.SimuleringResponse = withAuthRetry {
        val xml = xmlMapper.writeValueAsString(request).replace(Regex("ns\\d="), "xmlns:$0")
        val response = soap.call(SimulerAction.BEREGNING, xml)
        (json(response) ?: Beregning.empty(request)).intoDto()
    }

    fun simuler(request: rest.SimuleringRequest): rest.SimuleringResponse = withAuthRetry {
        val request = SimulerBeregningRequest.from(request)
        val xml = xmlMapper.writeValueAsString(request).replace(Regex("ns\\d="), "xmlns:$0")
        val response = soap.call(SimulerAction.BEREGNING, xml)
        (json(response) ?: Beregning.empty(request)).intoDto()
    }

    private fun <T> withAuthRetry(block: () -> T): T {
        return try {
            block()
        } catch (e: SoapException) {
            if (e.message?.contains("FailedAuthentication") == true) {
                wsLog.warn("STS-token feilet med FailedAuthentication, invaliderer cache og prøver på nytt")
                sts.invalidate()
                block()
            } else {
                throw e
            }
        }
    }

    fun json(xml: String): Beregning? = when (classify(xml)) {
        EnvelopeKind.FAULT -> fault(xml)
        EnvelopeKind.RESPONSE -> try {
            wsLog.debug("Forsøker å deserialisere simulerBeregningResponse")
            simulerBeregningResponse(xml).response?.simulering
        } catch (e: Throwable) {
            wsLog.error("Feilet deserialisering av simulerBeregningResponse")
            secureLog.error("Feilet deserialisering av simulerBeregningResponse: $xml", e)
            badGateway("Ugyldig respons fra Oppdragssystemet")
        }
        EnvelopeKind.UNKNOWN -> {
            wsLog.error("Ukjent SOAP-svar fra Oppdragssystemet")
            secureLog.error("Ukjent SOAP-svar fra Oppdragssystemet: $xml")
            badGateway("Ukjent svar fra Oppdragssystemet")
        }
    }

    private enum class EnvelopeKind { RESPONSE, FAULT, UNKNOWN }

    private fun classify(xml: String): EnvelopeKind = when {
        "<faultcode" in xml || ":Fault " in xml || ":Fault>" in xml -> EnvelopeKind.FAULT
        "simulerBeregningResponse" in xml -> EnvelopeKind.RESPONSE
        else -> EnvelopeKind.UNKNOWN
    }

    private fun simulerBeregningResponse(xml: String): soap.SimulerBeregningResponse =
        tryInto<soap.SimuleringResponse>(xml).simulerBeregningResponse

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
}

@JacksonXmlRootElement(localName = "Envelope", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
data class SoapResponse<T>(
    @param:JacksonXmlProperty(localName = "Header")
    val header: SoapHeader?,
    @param:JacksonXmlProperty(localName = "Body")
    val body: T,
)

data class SoapHeader(
    @param:JacksonXmlProperty(localName = "Action", namespace = "http://www.w3.org/2005/08/addressing")
    val action: String,
    @param:JacksonXmlProperty(localName = "MessageID", namespace = "http://www.w3.org/2005/08/addressing")
    val messageId: String,
)

data class SoapFault(
    @param:JacksonXmlProperty(localName = "Fault", namespace = "http://www.w3.org/2003/05/soap-envelope")
    val fault: Fault,
)

data class Fault(
    val faultcode: String,
    val faultstring: String,
    val detail: Map<String, Any> = emptyMap(),
)

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
