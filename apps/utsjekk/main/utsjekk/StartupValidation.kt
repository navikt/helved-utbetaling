package utsjekk

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import libs.jdbc.Jdbc
import libs.jdbc.context
import libs.jdbc.concurrency.connection
import libs.jdbc.concurrency.transaction
import libs.utils.appLog
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.AdminClient
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Properties
import java.util.concurrent.TimeUnit.SECONDS
import javax.sql.DataSource
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun validateStartupConfig(
    config: Config,
    timeout: Duration = 30.seconds,
) {
    validateStartupConfig(config, timeout, StartupValidationDependencies())
}

internal suspend fun validateStartupConfig(
    config: Config,
    timeout: Duration,
    dependencies: StartupValidationDependencies,
) {
    val timeoutCap = config.startupValidationTimeoutSeconds.seconds
    val effectiveTimeout = minOf(timeout, timeoutCap)

    try {
        withTimeout(timeoutCap) {
            withTimeout(timeout) {
                coroutineScope {
                    launch { dependencies.validateDb(config) }
                    launch { dependencies.validateKafka(config) }
                    launch { dependencies.validateExternalServices(config) }
                }
            }
        }
    } catch (e: StartupValidationException) {
        throw e
    } catch (e: TimeoutCancellationException) {
        throw StartupValidationException(
            message = "timed out after ${effectiveTimeout.inWholeSeconds}s",
            cause = e,
        )
    }
}

internal data class StartupValidationDependencies(
    val validateDb: suspend (Config) -> Unit = ::validateDatabaseConnection,
    val validateKafka: suspend (Config) -> Unit = ::validateKafkaConnection,
    val validateExternalServices: suspend (Config) -> Unit = ::validateExternalServices,
)

internal class StartupValidationException(
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

internal suspend fun wireWithExitOnFailure(config: Config) {
    try {
        validateStartupConfig(config)
    } catch (e: StartupValidationException) {
        appLog.error("Startup validation failed: ${e.message}")
        exitProcess(1)
    }
}

private suspend fun validateDatabaseConnection(config: Config) {
    try {
        val datasource = Jdbc.initialize(config.jdbc)
        withContext(datasource.context()) {
            transaction {
                coroutineContext.connection.prepareStatement("SELECT 1").use { stmt ->
                    stmt.executeQuery().use { rs ->
                        check(rs.next()) { "SELECT 1 returned no rows" }
                    }
                }
            }
        }
        datasource.closeQuietly()
    } catch (e: Exception) {
        throw StartupValidationException("database connectivity check failed", e)
    }
}

private fun DataSource.closeQuietly() {
    (this as? AutoCloseable)?.close()
}

private fun validateKafkaConnection(config: Config) {
    val properties = Properties().apply {
        this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = config.kafka.brokers
        this[AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG] = 2_000
        this[AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG] = 4_000
        this[AdminClientConfig.RETRIES_CONFIG] = 0
        this["socket.connection.setup.timeout.ms"] = 1_000
        this["socket.connection.setup.timeout.max.ms"] = 1_000
        config.kafka.ssl?.let { putAll(it.properties()) }
    }

    try {
        AdminClient.create(properties).use { adminClient ->
            adminClient.describeCluster().nodes().get(5, SECONDS)
        }
    } catch (e: Exception) {
        throw StartupValidationException("kafka cluster check failed", e)
    }
}

private suspend fun validateExternalServices(config: Config) {
    coroutineScope {
        config.externalServiceTargets().forEach { target ->
            launch {
                probeTcp(target.name, target.url)
            }
        }
    }
}

private fun probeTcp(name: String, url: URL) {
    val host = url.host
    val port = when {
        url.port != -1 -> url.port
        url.protocol == "https" -> 443
        else -> 80
    }

    try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 2_000)
        }
    } catch (e: Exception) {
        throw StartupValidationException("external service probe failed for $name ($host:$port)", e)
    }
}

private fun Config.externalServiceTargets(): List<ExternalServiceTarget> =
    listOf(
        ExternalServiceTarget("simulering", simulering.host),
        ExternalServiceTarget("azure token", azure.tokenEndpoint),
        ExternalServiceTarget("azure jwks", azure.jwks),
    ).distinctBy { "${it.url.protocol}://${it.url.host}:${it.url.effectivePort()}" }

private fun URL.effectivePort(): Int = when {
    port != -1 -> port
    protocol == "https" -> 443
    else -> 80
}

private data class ExternalServiceTarget(
    val name: String,
    val url: URL,
)
