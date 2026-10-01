package peisschtappern

import com.google.cloud.audit.AuditLog
import com.google.cloud.logging.v2.LoggingClient
import com.google.logging.v2.ListLogEntriesRequest
import com.google.logging.v2.LogEntry
import com.google.protobuf.util.JsonFormat
import com.google.protobuf.util.Timestamps
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class AuditLogEntry(
    val timestamp: String,
    val severity: String,
    val logName: String,
    val payload: String,
)

@Serializable
data class AuditLogPage(
    val entries: List<AuditLogEntry>,
    val nextPageToken: String?,
)

interface AuditLogReader {
    fun list(resourceName: String, fom: Instant?, tom: Instant?, pageSize: Int, pageToken: String?, filter: String? = null): AuditLogPage
}

/**
 * Leser fra et Cloud Logging resource name (prosjekt eller log view).
 * Autentiserer med Workload Identity (Application Default Credentials).
 */
class GcpAuditLogReader : AuditLogReader, AutoCloseable {
    private val clientDelegate = lazy { LoggingClient.create() }
    private val client: LoggingClient by clientDelegate

    override fun list(resourceName: String, fom: Instant?, tom: Instant?, pageSize: Int, pageToken: String?, filter: String?): AuditLogPage {
        val request = ListLogEntriesRequest.newBuilder()
            .addResourceNames(resourceName)
            .setOrderBy("timestamp desc")
            .setPageSize(pageSize)
            .apply {
                val conditions = listOfNotNull(
                    filter,
                    fom?.let { "timestamp>=\"$it\"" },
                    tom?.let { "timestamp<=\"$it\"" }
                )
                if (conditions.isNotEmpty()) setFilter(conditions.joinToString(" AND "))
            }
            .apply { if (pageToken != null) setPageToken(pageToken) }
            .build()

        val page = client.listLogEntries(request).page
        return AuditLogPage(
            entries = page.values.map(::toAuditLogEntry),
            nextPageToken = page.nextPageToken.takeIf { it.isNotBlank() },
        )
    }

    override fun close() {
        if (clientDelegate.isInitialized()) client.close()
    }
}

private val jsonPrinter = JsonFormat.printer()
    .usingTypeRegistry(JsonFormat.TypeRegistry.newBuilder().add(AuditLog.getDescriptor()).build())
    .omittingInsignificantWhitespace()

private fun toAuditLogEntry(entry: LogEntry) = AuditLogEntry(
    timestamp = Timestamps.toString(entry.timestamp),
    severity = entry.severity.name,
    logName = entry.logName,
    payload = when (entry.payloadCase) {
        LogEntry.PayloadCase.TEXT_PAYLOAD -> entry.textPayload
        LogEntry.PayloadCase.JSON_PAYLOAD -> jsonPrinter.print(entry.jsonPayload)
        LogEntry.PayloadCase.PROTO_PAYLOAD -> runCatching { jsonPrinter.print(entry.protoPayload) }.getOrElse { entry.protoPayload.toString() }
        else -> ""
    },
)

// Vi bruker dette filteret for å hente ut de samme manuelle (database)endringer
fun databaseAuditLogFilter(): String =
    """resource.type="cloudsql_database" AND protoPayload.methodName="cloudsql.instances.query" AND protoPayload.request.user=~"(?i)@nav[.]no$" """
        .trim()
