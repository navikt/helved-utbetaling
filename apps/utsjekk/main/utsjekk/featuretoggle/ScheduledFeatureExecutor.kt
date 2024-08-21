package utsjekk.featuretoggle

import io.getunleash.util.UnleashScheduledExecutor
import libs.utils.appLog
import java.util.concurrent.*

/**
 * Unleashed cacher features by default i minst 600 ms.
 * For å tillate hyppigere fetching (slik at testene ikke bruker veeldig lang tid,
 * endrer denne fra sekunder til millisekunder ved konfigurering av `.fetchTogglesInterval(300)`.
 */
@Suppress("UNCHECKED_CAST")
class ScheduledFeatureExecutor : UnleashScheduledExecutor {
    private val tf: ThreadFactory = ThreadFactory { runnable: Runnable ->
        val thread = Executors.defaultThreadFactory().newThread(runnable)
        thread.name = "unleash-api-executor"
        thread.isDaemon = true
        thread
    }

    private val scheduledThreadPoolExec = ScheduledThreadPoolExecutor(1, tf).apply {
        removeOnCancelPolicy = true
    }

    private val executorService = Executors.newSingleThreadExecutor(tf)

    override fun setInterval(command: Runnable, initDelayMs: Long, periodMs: Long): ScheduledFuture<*>? {
        return try {
            scheduledThreadPoolExec.scheduleAtFixedRate(command, initDelayMs, periodMs, TimeUnit.MILLISECONDS)
        } catch (e: RejectedExecutionException) {
            appLog.error("Unleash background task crashed", e)
            null
        }
    }

    override fun scheduleOnce(runnable: Runnable): Future<Void> {
        return executorService.submit(runnable) as Future<Void>
    }
}
