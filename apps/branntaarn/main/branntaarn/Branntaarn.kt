package branntaarn

import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import libs.utils.Log
import libs.utils.appLog
import libs.utils.erHelligdag
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Filter
import org.http4k.core.then
import org.http4k.filter.ResilienceFilters
import java.time.Duration
import java.time.LocalDateTime

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        Log.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    val config = Config()
    val http = httpRequestRetry(
        JavaHttpClient(requestModifier = { it.timeout(Duration.ofSeconds(60)) })
    )
    val peisschtappern = PeisschtappernClient(config, http = http)
    val slack = SlackClient(config, http = http)

    val branner = hentBranner(peisschtappern)
    brannalarmer(slack, branner)
    slukk(branner, peisschtappern)
}

fun hentBranner(peisschtappern: PeisschtappernClient) =
    manglendeKvittering(peisschtappern) +
    pendingMismatch(peisschtappern) +
    peisschtappern.dobbeltutbetalinger()

fun brannalarmer(slack: SlackClient, branner: List<Brann>) {
    slack.postPendingMismatches(branner.filterIsInstance<PendingMismatch>())
    slack.postAggregated(branner.filterIsInstance<ManglendeKvittering>().groupBy { it.fagsystem })
    slack.postDobbeltutbetalinger(branner.filterIsInstance<Dobbeltutbetaling>())
}

fun slukk(branner: List<Brann>, peisschtappern: PeisschtappernClient) {
    branner.forEach(peisschtappern::slukk)
}

fun manglendeKvittering(
    peisschtappern: PeisschtappernClient,
    now: LocalDateTime = LocalDateTime.now(),
): List<Brann> {
    if (!now.erVarseltid()) return emptyList()
    return peisschtappern.manglendeKvitteringer().filter { it.timeout.isBefore(now) }
}

fun pendingMismatch(
    peisschtappern: PeisschtappernClient,
    now: LocalDateTime = LocalDateTime.now(),
): List<Brann> {
    if (!now.erVarseltid()) return emptyList()
    return peisschtappern.pendingMismatches()
}

internal fun LocalDateTime.erVarseltid() = !toLocalDate().erHelligdag() && hour in 6..21

internal fun httpRequestRetry(client: HttpHandler): HttpHandler {
    val retry = Retry.of(
        "branntaarn-http",
        RetryConfig.custom<Response>()
            .maxAttempts(3)
            .intervalFunction(IntervalFunction.ofExponentialBackoff(1_000L, 2.0))
            .retryOnResult { it.status.serverError }
            .retryOnException { false }
            .consumeResultBeforeRetryAttempt { _, response -> response.close() }
            .build()
    )
    return ResilienceFilters.RetryFailures(retry, isError = { false })
        .then(Filter { next ->
            { request ->
                next(request).also { response ->
                    if (response.status.serverError) {
                        appLog.warn("HTTP ${request.method} ${request.uri.host} feilet med ${response.status}")
                    }
                }
            }
        })
        .then(client)
}