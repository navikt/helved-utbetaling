package utsjekk.simulering

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withContext
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.jdbc.Jdbc
import models.*
import models.kontrakter.oppdrag.Utbetalingsoppdrag
import utsjekk.Config
import utsjekk.TokenType
import utsjekk.iverksetting.IverksettingResultater
import utsjekk.iverksetting.UtbetalingId
import utsjekk.iverksetting.lagAndelData
import utsjekk.iverksetting.tilAndelData
import utsjekk.iverksetting.utbetalingsoppdrag.Utbetalingsgenerator
import utsjekk.utbetaling.UtbetalingsoppdragDto

class SimuleringClient(
    private val config: Config,
    private val client: HttpClient = HttpClientFactory.new(
        logLevel = LogLevel.ALL, 
        retries = null, 
        requestTimeoutMs = 120_000, 
        connectionTimeoutMs = 5000,
    ),
    private val azure: AzureTokenProvider = AzureTokenProvider(config.azure)
) {
    suspend fun simuler(
        utbetaling: UtbetalingsoppdragDto,
        token: TokenType,
    ): client.SimuleringResponse {
        val azureToken = when(token) {
            is TokenType.Obo -> azure.getOnBehalfOfToken(token.jwt, config.simulering.scope)
            is TokenType.Client -> azure.getClientCredentialsToken(config.simulering.scope)
        }

        val response = client.post("${config.simulering.host}/simuler") {
            bearerAuth(azureToken.access_token)
            contentType(ContentType.Application.Json)
            setBody(utbetaling)
        }

        if (response.status == HttpStatusCode.OK) {
            return response.body<client.SimuleringResponse>()
        }

        // TODO: Gå gjennom feilmeldinger og responser som sendes til konsument
        when (response.status) {
            HttpStatusCode.NotFound -> notFound(response.bodyAsText(), "simuleringsresultat")
            HttpStatusCode.Conflict -> conflict(response.bodyAsText(), "simuleringsresultat")
            HttpStatusCode.BadRequest -> badRequest(response.bodyAsText(), "simuleringsresultat")
            HttpStatusCode.BadGateway -> badGateway(response.bodyAsText(), "utsjekk-simulering")
            HttpStatusCode.ServiceUnavailable -> unavailable(response.bodyAsText(), "utsjekk-simulering")
            else -> error("HTTP ${response.status} feil fra utsjekk-simulering: ${response.bodyAsText()}")
        }
    }

    suspend fun hentSimuleringsresultatMedOppsummering(
        simulering: domain.Simulering,
        token: TokenType,
    ): api.SimuleringRespons? {
        val utbetalingsoppdrag = hentUtbetalingsoppdrag(simulering)
        if (utbetalingsoppdrag.utbetalingsperiode.isEmpty()) return null

        val request = utsjekk.simulering.client.SimuleringRequest.from(utbetalingsoppdrag)

        val azureToken = when(token) {
            is TokenType.Obo -> azure.getOnBehalfOfToken(token.jwt, config.simulering.scope)
            is TokenType.Client -> azure.getClientCredentialsToken(config.simulering.scope)
        }

        val response = client.post("${config.simulering.host}/simulering") {
            bearerAuth(azureToken.access_token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if(response.status == HttpStatusCode.OK) {
            val hentetSimulering = response.body<client.SimuleringResponse>()
            val detaljer = domain.SimuleringDetaljer.from(hentetSimulering, simulering.behandlingsinformasjon.fagsystem)
            return api.SimuleringRespons.from(detaljer)
        }

        when (response.status) {
            HttpStatusCode.NotFound -> notFound(response.bodyAsText(), "simuleringsresultat")
            HttpStatusCode.Conflict -> conflict(response.bodyAsText(), "simuleringsresultat")
            HttpStatusCode.BadRequest -> badRequest(response.bodyAsText(), "simuleringsresultat")
            HttpStatusCode.BadGateway -> badGateway(response.bodyAsText(), "utsjekk-simulering")
            HttpStatusCode.ServiceUnavailable -> unavailable(response.bodyAsText(), "utsjekk-simulering")
            else -> error("HTTP ${response.status} feil fra utsjekk-simulering: ${response.bodyAsText()}")
        }
    }

    private suspend fun hentUtbetalingsoppdrag(sim: domain.Simulering): Utbetalingsoppdrag {
        val forrigeTilkjenteYtelse = withContext(Jdbc.context) {
            sim.forrigeIverksetting?.let {
                val forrigeIverksetting = IverksettingResultater.hent(
                    UtbetalingId(
                        fagsystem = sim.behandlingsinformasjon.fagsystem,
                        sakId = sim.behandlingsinformasjon.fagsakId,
                        behandlingId = it.behandlingId,
                        iverksettingId = it.iverksettingId,
                    )
                )

                forrigeIverksetting.tilkjentYtelseForUtbetaling
            }
        }
        val sisteAndelPerKjede = forrigeTilkjenteYtelse
            ?.sisteAndelPerKjede
            ?.mapValues { it.value.tilAndelData() }
            ?: emptyMap()


        val beregnetUtbetalingsoppdrag = Utbetalingsgenerator.lagUtbetalingsoppdrag(
            behandlingsinformasjon = sim.behandlingsinformasjon,
            requested = sim.nyTilkjentYtelse.lagAndelData(),
            existing = forrigeTilkjenteYtelse?.lagAndelData() ?: emptyList(),
            lastExistingByKjede = sisteAndelPerKjede,
        )

        return beregnetUtbetalingsoppdrag.utbetalingsoppdrag
    }

}
