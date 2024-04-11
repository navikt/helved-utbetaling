package simulering

import com.ctc.wstx.exc.WstxEOFException
import com.ctc.wstx.exc.WstxIOException
import felles.secureLog
import jakarta.xml.ws.WebServiceException
import jakarta.xml.ws.soap.SOAPFaultException
import no.nav.common.cxf.CXFClient
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningFeilUnderBehandling
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerFpService
import simulering.dto.SimuleringRequestBody
import simulering.dto.SimuleringRequestBuilder
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

class SimuleringService(
    config: Config,
) {
    private val simulerFpService: SimulerFpService =
        CXFClient(SimulerFpService::class.java)
            .address(config.simulering.host)
            .timeout(20_000, 20_000)
            .configureStsForSystemUser(config.sts)
            .build()

    fun simuler(request: SimuleringRequestBody): Simulering? =
        runCatching {
            val req = SimuleringRequestBuilder(request).build()
            val res = simulerFpService.simulerBeregning(req)
            res.tilSimulering()
        }.onFailure { ex ->
            when (ex) {
                is SimulerBeregningFeilUnderBehandling -> simuleringFeilet(ex)
                is SOAPFaultException -> soapFault(ex)
                is WebServiceException -> webserviceFault(ex)
                else -> throw ex
            }
        }.getOrThrow()

    private fun simuleringFeilet(e: SimulerBeregningFeilUnderBehandling) {
        with(e.faultInfo.errorMessage) {
            when {
                contains("Personen finnes ikke") -> throw PersonFinnesIkkeException(this)
                contains("ugyldig") -> throw RequestErUgyldigException(this)
                else -> throw e
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
}


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
