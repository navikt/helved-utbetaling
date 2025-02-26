package urskog

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.dataformat.xml.*
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.client.plugins.logging.*
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.utils.secureLog
import libs.ws.*
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

    fun simuler()  {

    }

    private suspend fun getAzureToken(): String {
        return "Bearer ${azure.getClientCredentialsToken(config.proxy.scope).access_token}"
    }
}

