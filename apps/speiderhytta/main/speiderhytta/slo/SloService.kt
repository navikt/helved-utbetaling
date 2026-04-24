package speiderhytta.slo

import kotlinx.coroutines.withContext
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.transaction
import libs.utils.appLog
import speiderhytta.Metrics
import java.time.Instant

/**
 * Live SLO state for the JSON API + periodic snapshot writer.
 *
 * Live view per SLO:
 *   sli              = error_query / total_query (instant)
 *   error_budget     = (1 - objective/100) — total budget
 *   budget_remaining = 1 - sli/(1 - objective/100)   (clamped to [0, 1])
 */
class SloService(
    private val definitions: List<SloDefinition>,
    private val prometheus: PrometheusClient,
    private val metrics: Metrics,
    private val jdbcCtx: CoroutineDatasource,
) {

    suspend fun status(app: String): List<SloStatus> =
        definitions.filter { it.service == app }.map { evaluate(it) }

    suspend fun statusAll(): List<SloStatus> = definitions.map { evaluate(it) }

    /**
     * Persist one snapshot per defined SLO. Called on a schedule from the
     * entry point. Failures per-SLO are logged and ignored — partial snapshots
     * are still useful.
     */
    suspend fun snapshot(now: Instant = Instant.now()) {
        for (def in definitions) {
            try {
                val status = evaluate(def, now)
                withContext(jdbcCtx) {
                    transaction {
                        SloSnapshot(
                            app = status.app,
                            sloName = status.name,
                            objective = status.objective,
                            sliValue = status.sli,
                            errorBudgetRemaining = status.errorBudgetRemaining,
                            burnRate1h = status.burnRate1h,
                            burnRate6h = status.burnRate6h,
                            capturedAt = now,
                        ).insert()
                    }
                }
                metrics.sloSnapshotted(status.app, status.name, status.errorBudgetRemaining)
            } catch (t: Throwable) {
                appLog.warn("failed to snapshot SLO {}/{}", def.service, def.name, t)
            }
        }
    }

    private suspend fun evaluate(def: SloDefinition, now: Instant = Instant.now()): SloStatus {
        val errors = prometheus.instant(def.sli.events.errorQuery) ?: 0.0
        val total = prometheus.instant(def.sli.events.totalQuery) ?: 0.0
        val sli = if (total > 0) errors / total else 0.0
        val errorBudget = 1.0 - def.objective / 100.0
        val budgetRemaining = if (errorBudget > 0) (1.0 - sli / errorBudget).coerceIn(0.0, 1.0) else 1.0
        return SloStatus(
            app = def.service,
            name = def.name,
            objective = def.objective,
            sli = sli,
            errorBudget = errorBudget,
            errorBudgetRemaining = budgetRemaining,
            burnRate1h = null,
            burnRate6h = null,
            evaluatedAt = now,
        )
    }
}

data class SloStatus(
    val app: String,
    val name: String,
    val objective: Double,
    val sli: Double,
    val errorBudget: Double,
    val errorBudgetRemaining: Double,
    val burnRate1h: Double?,
    val burnRate6h: Double?,
    val evaluatedAt: Instant,
)
