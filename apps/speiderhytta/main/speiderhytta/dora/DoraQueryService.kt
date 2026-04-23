package speiderhytta.dora

import java.time.Duration
import java.time.Instant

/**
 * Per-app DORA aggregate. Computed fresh on each request from the
 * `deployment` and `incident` tables — there's not enough volume to justify
 * a materialized view.
 *
 * Lead time and MTTR are reported in seconds; helved-peisen formats them.
 */
data class DoraSummary(
    val app: String,
    val window: Window,
    val deployFrequencyPerDay: Double,
    val leadTimeMedianSeconds: Long?,
    val leadTimeP90Seconds: Long?,
    val changeFailureRate: Double,
    val mttrMedianSeconds: Long?,
    val deploymentCount: Int,
    val incidentCount: Int,
) {
    data class Window(val from: Instant, val to: Instant)
}

/**
 * Computes per-app DORA summaries over a rolling window.
 *
 * Defaults to a 30-day window which is the standard DORA cadence and gives
 * stable values for our deployment volume.
 *
 * **CFR formula:** `(deploy failures + incidents) / total non-cancelled attempts`.
 * Cancelled deploys (manual stops, skipped jobs) are excluded from both
 * numerator and denominator — they aren't real change attempts.
 */
class DoraQueryService(
    private val targetEnv: String = "prod-gcp",
    private val window: Duration = Duration.ofDays(30),
) {
    suspend fun summary(app: String, now: Instant = Instant.now()): DoraSummary {
        val from = now.minus(window)
        val successful = Deployment.selectSuccessfulFor(app, targetEnv, from)
        val allAttempts = Deployment.selectAllFor(app, targetEnv, from)
        val incidents = Incident.selectFor(app, from)

        val days = window.toDays().coerceAtLeast(1)
        val deployFreq = successful.size.toDouble() / days
        val leadTimes = successful.map { it.leadTimeSeconds }.sorted()
        val mttrValues = incidents.mapNotNull { it.mttrSeconds }.sorted()

        val deployFailures = allAttempts.count { it.outcome == "failure" }
        val cfrDenominator = allAttempts.count { it.outcome != "cancelled" }
        val incidentsLinkedToDeploys = incidents.count { it.causedByDeploymentId != null }
        val cfr = if (cfrDenominator == 0) 0.0
                  else (deployFailures + incidentsLinkedToDeploys).toDouble() / cfrDenominator

        return DoraSummary(
            app = app,
            window = DoraSummary.Window(from, now),
            deployFrequencyPerDay = deployFreq,
            leadTimeMedianSeconds = leadTimes.percentile(50),
            leadTimeP90Seconds = leadTimes.percentile(90),
            changeFailureRate = cfr,
            mttrMedianSeconds = mttrValues.percentile(50),
            deploymentCount = successful.size,
            incidentCount = incidents.size,
        )
    }

    suspend fun summaries(apps: List<String>, now: Instant = Instant.now()): List<DoraSummary> =
        apps.map { summary(it, now) }

    private fun List<Long>.percentile(p: Int): Long? {
        if (isEmpty()) return null
        val rank = ((p / 100.0) * (size - 1)).toInt().coerceIn(0, size - 1)
        return this[rank]
    }
}
