package libs.utils

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import java.io.IOException
import java.io.OutputStream
import java.net.Socket

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
        connect()
    }
}
