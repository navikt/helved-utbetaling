package peisschtappern

import com.google.cloud.audit.AuditLog
import com.google.cloud.logging.v2.LoggingClient
import com.google.logging.v2.ListLogEntriesRequest
import com.google.logging.v2.LogEntry
import com.google.protobuf.util.JsonFormat
import com.google.protobuf.util.Timestamps
import kotlinx.serialization.Serializable

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
    fun list(filter: String?, pageSize: Int, pageToken: String?): AuditLogPage
}

/**
 * Leser fra en log view i Cloud Logging, f.eks.
 * projects/helved-prod-119e/locations/europe-north1/buckets/TeamAudit/views/_AllLogs.
 * Autentiserer med Workload Identity (Application Default Credentials).
 * Appens GSA trenger roles/logging.viewAccessor på viewet.
 */
class GcpAuditLogReader(private val view: String) : AuditLogReader, AutoCloseable {
    private val client: LoggingClient by lazy { LoggingClient.create() }

    override fun list(filter: String?, pageSize: Int, pageToken: String?): AuditLogPage {
        val request = ListLogEntriesRequest.newBuilder()
            .addResourceNames(view)
            .setOrderBy("timestamp desc")
            .setPageSize(pageSize)
            .apply { if (filter != null) setFilter(filter) }
            .apply { if (pageToken != null) setPageToken(pageToken) }
            .build()

        val page = client.listLogEntries(request).page
        return AuditLogPage(
            entries = page.values.map(::toAuditLogEntry),
            nextPageToken = page.nextPageToken.takeIf { it.isNotBlank() },
        )
    }

    override fun close() = client.close()
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
