package utsjekk.clients

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.http.*
import kotlinx.coroutines.withContext
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.postgres.Jdbc
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import utsjekk.*
import utsjekk.iverksetting.UtbetalingId
import utsjekk.iverksetting.lagAndelData
import utsjekk.iverksetting.resultat.IverksettingResultater
import utsjekk.iverksetting.tilAndelData
import utsjekk.iverksetting.utbetalingsoppdrag.Utbetalingsgenerator
import utsjekk.simulering.*
import utsjekk.simulering.oppsummering.OppsummeringGenerator
import utsjekk.simulering.oppsummering.OppsummeringGeneratorNy

class SimuleringClient(
    private val config: Config,
    private val client: HttpClient = HttpClientFactory.new(LogLevel.ALL),
    private val azure: AzureTokenProvider = AzureTokenProvider(config.azure)
) {

    suspend fun hentSimuleringsresultatMedOppsummering(
        simulering: Simulering,
        oboToken: String,
    ): api.SimuleringRespons? {
        val utbetalingsoppdrag = hentUtbetalingsoppdrag(simulering)
        if (utbetalingsoppdrag.utbetalingsperiode.isEmpty()) return null

        val request = utsjekk.simulering.client.SimuleringRequest.from(utbetalingsoppdrag)

        val response = client.post("${config.simulering.host}/simulering") {
            bearerAuth(azure.getOnBehalfOfToken(oboToken, config.simulering.scope).access_token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val hentetSimulering = when (response.status) {
            HttpStatusCode.OK -> response.body<client.SimuleringResponse>()
            HttpStatusCode.NotFound -> notFound(response.bodyAsText(), "simuleringsresultat")
            HttpStatusCode.Conflict -> conflict(response.bodyAsText(), "simuleringsresultat")
            HttpStatusCode.BadRequest -> badRequest(response.bodyAsText(), "simuleringsresultat")
            HttpStatusCode.ServiceUnavailable -> unavailable(response.bodyAsText(), "utsjekk-simulering")
            else -> error("HTTP ${response.status} feil fra utsjekk-simulering: ${response.bodyAsText()}")
        }


        val detaljer = SimuleringDetaljer.from(hentetSimulering, simulering.behandlingsinformasjon.fagsystem)
        return if (config.simulering.scope.contains("dev-gcp")) {
            OppsummeringGeneratorNy.lagOppsummering(detaljer)
        } else {
            OppsummeringGenerator.lagOppsummering(detaljer)
        }
    }

    private suspend fun hentUtbetalingsoppdrag(sim: Simulering): Utbetalingsoppdrag {
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
            nyeAndeler = sim.nyTilkjentYtelse.lagAndelData(),
            forrigeAndeler = forrigeTilkjenteYtelse?.lagAndelData() ?: emptyList(),
            sisteAndelPerKjede = sisteAndelPerKjede,
        )

        return beregnetUtbetalingsoppdrag.utbetalingsoppdrag
    }

}
