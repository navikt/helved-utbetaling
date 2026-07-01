package libs.utils

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.CoreConstants
import ch.qos.logback.core.OutputStreamAppender
import ch.qos.logback.core.encoder.EncoderBase
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun logger(name: String): Logger = LoggerFactory.getLogger(name)

val secureLog: Logger = logger("secureLog")
val auditLog: Logger = logger("audit")
val appLog: Logger = logger("appLog")
val jdbcLog: Logger = logger("jdbc")
val dryrunLog: Logger = logger("dryrun")

class LogTcpAppender: OutputStreamAppender<ILoggingEvent>() {
    var destination: String = ""
    private var socket: Socket? = null

    override fun start() {
        connect()
        super.start()
    }

    override fun writeOut(event: ILoggingEvent) {
        try {
            super.writeOut(event)
        } catch (e: IOException) {
            addWarn("TCP write failed, reconnecting: ${e.message}")
            reconnect()
            try {
                super.writeOut(event)
            } catch (e2: IOException) {
                addError("TCP reconect failed, dropping event", e2)
            }
        }
    }

    override fun stop() {
        runCatching { socket?.close() }
        super.stop()
    }

    private fun connect() {
        val (host, port) = destination.split(":")
        socket = Socket(host, port.toInt())
        outputStream = socket!!.getOutputStream()
    }

    private fun reconnect() {
        runCatching { socket?.close() }
        try {
            connect()
        } catch (e: IOException) {
            addError("TCP reconnect failed: ${e.message}")
            outputStream = OutputStream.nullOutputStream()
        }
    }
}

/**
 * Logstash-compatible JSON encoder for TCP secure-log shipping.
 * Produces the same schema as LogstashEncoder: @timestamp, @version, message,
 * logger_name, thread_name, level, level_value, stack_trace, plus MDC and customFields.
 */
class LogJsonEncoder : EncoderBase<ILoggingEvent>() {
    var customFields: String? = null

    override fun headerBytes(): ByteArray = EMPTY
    override fun footerBytes(): ByteArray = EMPTY

    override fun encode(event: ILoggingEvent): ByteArray {
        val sb = StringBuilder(512)
        sb.append('{')
        sb.sep().quoted("message", event.formattedMessage.escape())
        sb.sep().quoted("logger_name", event.loggerName)
        sb.sep().quoted("thread_name", event.threadName)
        sb.sep().quoted("level", event.level.toString())
        sb.sep().raw("level_value", event.level.toInt().toString())

        if (event.throwableProxy != null) {
            val stackTrace = ThrowableProxyUtil.asString(event.throwableProxy)
            sb.sep().quoted("stack_trace", stackTrace.escape())
        }

        for ((key, value) in event.mdcPropertyMap) {
            sb.sep().quoted(key, value.escape())
        }

        customFields?.let { fields ->
            val inner = fields.trim().removePrefix("{").removeSuffix("}")
            if (inner.isNotEmpty()) {
                sb.sep().append(inner)
            }
        }

        sb.append('}')
        sb.append(CoreConstants.JSON_LINE_SEPARATOR)
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun StringBuilder.sep(): StringBuilder = append(',')

    private fun StringBuilder.quoted(key: String, value: String): StringBuilder =
        append('"').append(key).append("\":\"").append(value).append('"')

    private fun StringBuilder.raw(key: String, value: String): StringBuilder =
        append('"').append(key).append("\":").append(value)

    private fun String.escape(): String = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    companion object {
        private val EMPTY = ByteArray(0)
        private val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .withZone(ZoneOffset.UTC)
    }
}

