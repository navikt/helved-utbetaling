package speiderhytta.dora

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.transaction
import libs.utils.appLog
import speiderhytta.Metrics
import java.time.Instant
import kotlin.time.Duration

/**
 * Generic poller loop: load cursor → run task → save cursor → sleep → repeat.
 *
 * Survives individual failures: errors are logged and the cursor is NOT advanced,
 * so the next tick reprocesses the same window. Both pollers in this app are
 * idempotent (ON CONFLICT DO NOTHING / DO UPDATE), so reprocessing is safe.
 */
class Poller(
    private val name: String,
    private val interval: Duration,
    private val metrics: Metrics,
    private val jdbcCtx: CoroutineDatasource,
    private val initialLookback: Instant = Instant.now().minusSeconds(60 * 60 * 24),
    private val task: suspend (Instant) -> Instant,
) {
    fun launchIn(scope: CoroutineScope): Job = scope.launch(Dispatchers.IO) {
        while (true) {
            val started = System.currentTimeMillis()
            try {
                tick()
                metrics.pollerSucceeded(name)
            } catch (t: Throwable) {
                appLog.error("poller {} failed", name, t)
                metrics.pollerError(name)
            } finally {
                metrics.pollerDuration(name, System.currentTimeMillis() - started)
            }
            delay(interval)
        }
    }

    private suspend fun tick() {
        val since = withContext(jdbcCtx) {
            transaction { PollerCursor.load(name)?.lastSeenTs ?: initialLookback }
        }
        val advanced = task(since)
        if (advanced.isAfter(since)) {
            withContext(jdbcCtx) {
                transaction {
                    PollerCursor(poller = name, lastSeenTs = advanced).save()
                }
            }
        }
    }
}
