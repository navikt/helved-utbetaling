package utsjekk.simulering

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.jdbc.concurrency.CoroutineDatasource
import models.*
import utsjekk.Config
import utsjekk.TokenType
import utsjekk.iverksetting.*
import utsjekk.iverksetting.UtbetalingId
import utsjekk.utbetaling.UtbetalingsoppdragDto

class SimuleringClient(
    private val config: Config,
    private val jdbcCtx: CoroutineDatasource,
    private val json: Json = libs.kotlinx.KotlinxJson,
    private val client: HttpClient = HttpClientFactory.new(
        json = json,
        logLevel = LogLevel.ALL,
        retries = 3,
        requestTimeoutMs = 120_000,
        connectionTimeoutMs = 5000,
    ),
    private val azure: AzureTokenProvider = AzureTokenProvider(json, config.azure)
) {
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

        val response = client.post("${config.simulering.host}/simuler/legacy") {
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
            HttpStatusCode.BadGateway -> badGateway(response.bodyAsText(), "simulering")
            HttpStatusCode.ServiceUnavailable -> unavailable(response.bodyAsText(), "simulering")
            else -> error("HTTP ${response.status} feil fra simulering: ${response.bodyAsText()}")
        }
    }

    private suspend fun hentUtbetalingsoppdrag(sim: domain.Simulering): Utbetalingsoppdrag {
        val forrigeTilkjenteYtelse = withContext(jdbcCtx) {
            sim.forrigeIverksetting?.let {
                val forrigeIverksetting = IverksettingService.hent(
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
