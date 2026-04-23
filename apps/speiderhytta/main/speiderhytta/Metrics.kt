package speiderhytta

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Speiderhytta's own Prometheus metrics.
 *
 * NB: DORA aggregates (deployment frequency, lead time, CFR, MTTR) are
 * computed by querying the database via the REST API rather than exposed as
 * Prometheus metrics. helved-peisen is the consumer; we keep these counters
 * to monitor speiderhytta itself.
 */
class Metrics(private val registry: MeterRegistry) {

    private val deploysObserved: Counter = Counter
        .builder("speiderhytta_deploys_observed_total")
        .description("Number of deploy attempts observed from GitHub Actions")
        .register(registry)

    private val incidentsObserved: Counter = Counter
        .builder("speiderhytta_incidents_observed_total")
        .description("Number of incident issues observed from GitHub")
        .register(registry)

    private val pollerErrors: Counter = Counter
        .builder("speiderhytta_poller_errors_total")
        .description("Polling errors")
        .register(registry)

    private val pollerSuccessTimestamps = ConcurrentHashMap<String, AtomicLong>()

    fun deployObserved() = deploysObserved.increment()
    fun incidentObserved() = incidentsObserved.increment()
    fun pollerError(poller: String) {
        registry.counter("speiderhytta_poller_errors_total", "poller", poller).increment()
        pollerErrors.increment()
    }

    fun pollerSucceeded(poller: String) {
        val ref = pollerSuccessTimestamps.computeIfAbsent(poller) {
            val atomic = AtomicLong(Instant.now().epochSecond)
            Gauge.builder("speiderhytta_last_successful_poll_seconds", atomic) { it.get().toDouble() }
                .description("Unix epoch seconds of last successful poll")
                .tag("poller", poller)
                .register(registry)
            atomic
        }
        ref.set(Instant.now().epochSecond)
    }

    fun pollerDuration(poller: String, ms: Long) {
        Timer.builder("speiderhytta_poller_duration_seconds")
            .description("Polling duration")
            .tag("poller", poller)
            .register(registry)
            .record(java.time.Duration.ofMillis(ms))
    }

    fun sloSnapshotted(app: String, sloName: String, errorBudgetRemaining: Double) {
        DistributionSummary.builder("speiderhytta_slo_error_budget_remaining")
            .description("Last observed error budget remaining (0..1)")
            .tags(listOf(Tag.of("app", app), Tag.of("slo", sloName)))
            .register(registry)
            .record(errorBudgetRemaining)
    }
}
