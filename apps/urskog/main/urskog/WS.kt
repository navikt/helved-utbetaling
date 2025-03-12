package urskog

import io.ktor.client.plugins.logging.*
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.ws.*
import libs.xml.XMLMapper
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.*

private object SimulerAction {
    private const val HOST = "http://nav.no"
    private const val PATH = "system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt"
    private const val SERVICE = "simulerFpService"
    const val BEREGNING = "$HOST/$PATH/$SERVICE/simulerBeregning"
    const val SEND_OPPDRAG = "$HOST/$PATH/$SERVICE/sendInnOppdragRequest"
}

class SimuleringService(
    private val config: Config,
) {
    private val http = HttpClientFactory.new(LogLevel.ALL)
    private val azure = AzureTokenProvider(config.azure)
    private val sts = StsClient(config.simulering.sts, http, proxyAuth = ::getAzureToken)
    private val soap = SoapClient(config.simulering, sts, http, proxyAuth = ::getAzureToken)
    private val requestMapper: XMLMapper<SimulerBeregningRequest> = XMLMapper(false)
    private val responseMapper: XMLMapper<SimulerBeregningResponse> = XMLMapper(false)

    suspend fun simuler(request: SimulerBeregningRequest): SimulerBeregningResponse  {
        val response = soap.call(SimulerAction.BEREGNING, requestMapper.writeValueAsString(request))
        return responseMapper.readValue(response)
    }

    private suspend fun getAzureToken(): String {
        return "Bearer ${azure.getClientCredentialsToken(config.proxy.scope).access_token}"
    }
}
