package utsjekk

import TestData
import TestRuntime
import fakes.Azp
import httpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import models.DpUtbetaling
import models.Fagsystem
import models.Info
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import utsjekk.iverksetting.RandomOSURId
import utsjekk.iverksetting.SakId
import utsjekk.utbetaling.PeriodeType
import utsjekk.utbetaling.UtbetalingsperiodeApi
import utsjekk.utbetaling.UtbetalingApi
import utsjekk.utbetaling.dagpenger
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class MetricsTest {

    @Test
    fun `eksponerer akkurat fem utsjekk metrikker og teller iverksetting ok`() = runTest(TestRuntime.context) {
        val dto = TestData.dto.iverksetting()
        val before = okIverksettingCount()

        val response = httpClient.post("/api/iverksetting/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(dto)
        }

        assertEquals(HttpStatusCode.Accepted, response.status)

        val scrape = scrapeMetrics()
        assertEquals(
            setOf(
                "helved_utsjekk_iverksetting_total",
                "helved_utsjekk_iverksetting_seconds",
                "helved_utsjekk_dryrun_request_total",
                "helved_utsjekk_dryrun_seconds",
                "helved_utsjekk_status_consumer_lag_seconds"
            ),
            metricNames(scrape)
                .filterNot { it.endsWith("_max") }
                .toSet()
        )
        assertTrue(okIverksettingCount() > before)
        assertFalse(scrape.contains(dto.sakId))
        assertFalse(scrape.contains(dto.behandlingId))
        assertFalse(scrape.contains(dto.personident.verdi))
        assertTrue(scrape.contains("helved_utsjekk_iverksetting_seconds_count "))
        assertFalse(scrape.contains("helved_utsjekk_iverksetting_seconds_count{"))
    }

    @Test
    fun `dryrun endpoint tags er bounded til v1 v2 v3`() = runTest {
        val uid = UUID.randomUUID()
        val v1 = httpClient.post("/utbetalinger/$uid/simuler") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(
                UtbetalingApi.dagpenger(
                    vedtakstidspunkt = LocalDate.of(2026, 1, 1),
                    periodeType = PeriodeType.MND,
                    perioder = listOf(UtbetalingsperiodeApi(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 24_000u))
                )
            )
        }
        assertEquals(HttpStatusCode.OK, v1.status)

        val v2 = httpClient.post("/api/simulering/v2") {
            bearerAuth(TestRuntime.azure.generateToken())
            contentType(ContentType.Application.Json)
            setBody(
                TestData.dto.api.simuleringRequest(
                    sakId = SakId(RandomOSURId.generate()),
                    utbetalinger = emptyList()
                )
            )
        }
        assertEquals(HttpStatusCode.NoContent, v2.status)

        val transactionId = UUID.randomUUID().toString()
        val asyncRequest = async {
            httpClient.post("/api/dryrun/dagpenger") {
                bearerAuth(TestRuntime.azure.generateToken(azp_name = Azp.DAGPENGER))
                contentType(ContentType.Application.Json)
                header("Transaction-ID", transactionId)
                setBody(
                    DpUtbetaling(
                        dryrun = true,
                        behandlingId = "behandling",
                        sakId = "sakId",
                        ident = "12345678910",
                        vedtakstidspunktet = LocalDateTime.now(),
                        utbetalinger = listOf(
                            models.DpUtbetalingsdag(
                                meldeperiode = "2026-01",
                                dato = LocalDate.of(2026, 1, 1),
                                sats = 1000u,
                                utbetaltBeløp = 1000u,
                                utbetalingstype = models.Utbetalingstype.Dagpenger,
                            )
                        )
                    )
                )
            }
        }
        TestRuntime.topics.dryrunDp.produce(transactionId) {
            Info(
                status = Info.Status.OK_UTEN_ENDRING,
                fagsystem = Fagsystem.DAGPENGER,
                message = "ok uten endring"
            )
        }
        assertEquals(HttpStatusCode.Found, asyncRequest.await().status)

        val invalid = httpClient.get("/api/dryrun/ukjent")
        assertEquals(HttpStatusCode.NotFound, invalid.status)

        val scrape = scrapeMetrics()
        assertEquals(setOf("v1", "v2", "v3"), endpointTags(scrape, "helved_utsjekk_dryrun_request_total"))
        assertEquals(setOf("v1", "v2", "v3"), endpointTags(scrape, "helved_utsjekk_dryrun_seconds_sum"))
    }

    @Test
    fun `status consumer lag gauge er ikke negativ og går ned etter consume`() = runTest(TestRuntime.context) {
        TestRuntime.metrics.statusConsumed(System.currentTimeMillis() - 3_000)
        val before = gaugeValue(scrapeMetrics(), "helved_utsjekk_status_consumer_lag_seconds")

        TestRuntime.metrics.statusConsumed(System.currentTimeMillis())

        val after = gaugeValue(scrapeMetrics(), "helved_utsjekk_status_consumer_lag_seconds")
        assertTrue(before >= 0.0)
        assertTrue(after >= 0.0)
        assertTrue(after < before)
    }

    private suspend fun scrapeMetrics(): String {
        val response = httpClient.get("/actuator/metric")
        assertEquals(HttpStatusCode.OK, response.status)
        return response.bodyAsText()
    }

    private fun okIverksettingCount(): Double =
        TestRuntime.meterRegistry.get("helved_utsjekk_iverksetting_total")
            .tag("result", "ok")
            .counter()
            .count()

    private fun metricNames(scrape: String): Set<String> =
        Regex("^# (?:HELP|TYPE) (helved_utsjekk_[a-z_]+)", RegexOption.MULTILINE)
            .findAll(scrape)
            .map { it.groupValues[1] }
            .toSet()

    private fun endpointTags(scrape: String, metric: String): Set<String> =
        Regex("^$metric\\{[^}]*endpoint=\"([^\"]+)\"[^}]*}.*$", RegexOption.MULTILINE)
            .findAll(scrape)
            .map { it.groupValues[1] }
            .toSet()

    private fun gaugeValue(scrape: String, metric: String): Double =
        Regex("^$metric ([0-9.E+-]+)$", RegexOption.MULTILINE)
            .find(scrape)
            ?.groupValues
            ?.get(1)
            ?.toDouble()
            ?: error("Fant ikke $metric i scrape")
}
