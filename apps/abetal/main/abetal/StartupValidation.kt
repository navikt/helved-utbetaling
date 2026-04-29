package abetal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import libs.utils.appLog
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClient
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.system.exitProcess
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class StartupChecks(
    val kafka: suspend (Config) -> Unit = ::validateKafkaStartup,
)

suspend fun validateStartupConfig(
    config: Config,
    timeout: Duration = config.startupValidationTimeoutSeconds.seconds,
    checks: StartupChecks = StartupChecks(),
) {
    val effectiveTimeout = timeout.coerceAtMost(30.seconds)

    try {
        withTimeout(effectiveTimeout) {
            coroutineScope {
                launch {
                    runStartupCheck("Kafka") {
                        checks.kafka(config)
                    }
                }
            }
        }
    } catch (e: TimeoutCancellationException) {
        throw IllegalStateException(
            "startup validation timed out after ${effectiveTimeout.inWholeSeconds}s",
            e,
        )
    }
}

internal suspend fun validateStartupConfigOrExit(config: Config) {
    try {
        validateStartupConfig(config)
    } catch (e: Throwable) {
        appLog.error("Startup validation failed: ${e.message}")
        exitProcess(1)
    }
}

private suspend fun validateKafkaStartup(config: Config) {
    runCancellableCheck {
        AdminClient.create(kafkaAdminProperties(config)).use { admin ->
            admin.describeCluster().nodes().get(5, SECONDS)
        }
    }
}

private fun kafkaAdminProperties(config: Config) = Properties().apply {
    this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = config.kafka.brokers
    config.kafka.ssl?.properties()?.let(::putAll)
}

private suspend fun runStartupCheck(name: String, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw IllegalStateException("$name startup validation failed: ${e.message}", e)
    }
}

private suspend fun runCancellableCheck(block: () -> Unit) {
    suspendCancellableCoroutine { continuation ->
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit {
            try {
                block()
                if (continuation.isActive) continuation.resume(Unit)
            } catch (e: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(e)
            } finally {
                executor.shutdownNow()
            }
        }

        continuation.invokeOnCancellation {
            future.cancel(true)
            executor.shutdownNow()
        }
    }
}
