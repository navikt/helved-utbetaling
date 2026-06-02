package simulering

import kotlinx.serialization.Serializable
import libs.utils.secureLog
import models.badGateway
import models.badRequest
import models.conflict
import models.forbidden
import models.notFound
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.DefaultXmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.OutputKind
import simulering.models.rest.rest
import simulering.models.soap.soap
import simulering.models.soap.soap.Beregning
import simulering.models.soap.soap.SimulerBeregningRequest

private object SimulerAction {
    private const val HOST = "http://nav.no"
    private const val PATH = "system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt"
    private const val SERVICE = "simulerFpService"
    const val BEREGNING = "$HOST/$PATH/$SERVICE/simulerBeregning"
    const val SEND_OPPDRAG = "$HOST/$PATH/$SERVICE/sendInnOppdragRequest"
}

@Suppress("DEPRECATION")
val xml: XML = XML {
    xmlDeclMode = XmlDeclMode.None
    indent = 2
    autoPolymorphic = true
}

// Separate instance for deserialization that treats primitives as elements
@OptIn(ExperimentalXmlUtilApi::class)
val xmlDeserializer: XML = XML.Companion.recommended_1_0 {
    xmlDeclMode = XmlDeclMode.None
    indentString = "  "
    policy = DefaultXmlSerializationPolicy.Builder10().apply {
        ignoreUnknownChildren()
        defaultPrimitiveOutputKind = OutputKind.Element
        defaultObjectOutputKind = OutputKind.Element
        isInlineCollapsedDefault = true
        verifyElementOrder = false
        throwOnRepeatedElement = false
    }.build()
}

@Serializable
data class FaultDetail(
    val simulerBeregningFeilUnderBehandling: FeilUnderBehandling? = null,
    val CICSFault: String? = null,
)

@Serializable
data class FeilUnderBehandling(
    val errorMessage: String? = null,
)

data class Fault(
    val faultcode: String,
    val faultstring: String,
    val detail: FaultDetail? = null,
)

class SimuleringService(private val soap: Soap, private val sts: Sts) {

    fun simuler(request: SimulerBeregningRequest): rest.SimuleringResponse = withAuthRetry {
        val xmlStr = xml.encodeToString(SimulerBeregningRequest.serializer(), request)
        val response = soap.call(SimulerAction.BEREGNING, xmlStr)
        (json(response) ?: Beregning.empty(request)).intoDto()
    }

    fun simuler(request: rest.SimuleringRequest): rest.SimuleringResponse = withAuthRetry {
        val req = SimulerBeregningRequest.from(request)
        val xmlStr = xml.encodeToString(SimulerBeregningRequest.serializer(), req)
        val response = soap.call(SimulerAction.BEREGNING, xmlStr)
        (json(response) ?: Beregning.empty(req)).intoDto()
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

    fun json(xmlStr: String): Beregning? = when (classify(xmlStr)) {
        EnvelopeKind.FAULT -> fault(xmlStr)
        EnvelopeKind.RESPONSE -> try {
            wsLog.debug("Forsøker å deserialisere simulerBeregningResponse")
            simulerBeregningResponse(xmlStr)
        } catch (e: Throwable) {
            wsLog.error("Feilet deserialisering av simulerBeregningResponse")
            secureLog.error("Feilet deserialisering av simulerBeregningResponse: $xmlStr", e)
            badGateway("Ugyldig respons fra Oppdragssystemet")
        }
        EnvelopeKind.UNKNOWN -> {
            wsLog.error("Ukjent SOAP-svar fra Oppdragssystemet")
            secureLog.error("Ukjent SOAP-svar fra Oppdragssystemet: $xmlStr")
            badGateway("Ukjent svar fra Oppdragssystemet")
        }
    }

    private enum class EnvelopeKind { RESPONSE, FAULT, UNKNOWN }

    private fun classify(xmlStr: String): EnvelopeKind = when {
        "<faultcode" in xmlStr || ":Fault " in xmlStr || ":Fault>" in xmlStr -> EnvelopeKind.FAULT
        "simulerBeregningResponse" in xmlStr -> EnvelopeKind.RESPONSE
        else -> EnvelopeKind.UNKNOWN
    }

    private fun simulerBeregningResponse(xmlStr: String): Beregning? {
        // Extract <simulerBeregningResponse>...</simulerBeregningResponse> content
        val responseBody = extractElement(xmlStr, "simulerBeregningResponse")
            ?: return null

        // Parse the inner response element
        val responseElement = extractElement(responseBody, "response")
            ?: return null

        val simuleringElement = extractElement(responseElement, "simulering")
            ?: return null

        // Strip all xmlns declarations and namespace prefixes for clean parsing
        val cleanXml = "<simulering>${stripNamespaces(simuleringElement)}</simulering>"
        return xmlDeserializer.decodeFromString(Beregning.serializer(), cleanXml)
    }

    private fun fault(xmlStr: String): Nothing {
        wsLog.debug("Forsøker å deserialisere fault")
        val faultcode = extractElementText(xmlStr, "faultcode") ?: "unknown"
        val faultstring = extractElementText(xmlStr, "faultstring") ?: "unknown"

        val detail = extractFaultDetail(xmlStr)
        val fault = Fault(faultcode, faultstring, detail)
        logAndThrow(fault)
    }

    private fun extractFaultDetail(xmlStr: String): FaultDetail? {
        val detailContent = extractElement(xmlStr, "detail") ?: return null

        val errorMessage = extractElementText(detailContent, "errorMessage")
        val cicsFault = extractElementText(detailContent, "CICSFault")

        val feilUnderBehandling = if (errorMessage != null) FeilUnderBehandling(errorMessage) else null

        return if (feilUnderBehandling != null || cicsFault != null) {
            FaultDetail(feilUnderBehandling, cicsFault)
        } else null
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
        val cicsFault = fault.detail?.CICSFault ?: soapError(fault)

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
        val errorMessage = fault.detail?.simulerBeregningFeilUnderBehandling?.errorMessage ?: soapError(fault)
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

/** Extract inner content of an element (handles namespace prefixes) */
private fun extractElement(xml: String, localName: String): String? {
    // Match <prefix:localName or <localName with optional attributes
    val openPattern = Regex("""<(?:\w+:)?$localName(?:\s[^>]*)?>""")
    val closePattern = Regex("""</(?:\w+:)?$localName\s*>""")

    val openMatch = openPattern.find(xml) ?: return null
    val closeMatch = closePattern.find(xml, openMatch.range.last) ?: return null

    return xml.substring(openMatch.range.last + 1, closeMatch.range.first)
}

/** Extract text content of a simple element */
private fun extractElementText(xml: String, localName: String): String? {
    val pattern = Regex("""<(?:\w+:)?$localName(?:\s[^>]*)?>([^<]*)</(?:\w+:)?$localName\s*>""")
    return pattern.find(xml)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
}

/** Strip xmlns declarations and namespace prefixes from XML elements */
private fun stripNamespaces(xml: String): String =
    xml.replace(Regex("""\s+xmlns(?::\w+)?="[^"]*""""), "")
        .replace(Regex("""<(\w+):"""), "<")
        .replace(Regex("""</(\w+):"""), "</")

