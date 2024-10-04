package fakes

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import port
import java.net.InetAddress
import java.net.URI

class LeaderElectorFake : AutoCloseable {
    private val elector = embeddedServer(Netty, port = 0, module = Application::elector).apply { start() }
    val url by lazy { "http://localhost:${elector.port()}".let(::URI).toURL() }
    override fun close() = elector.stop(0, 0)
}

private fun Application.elector() {
    routing {
        get {
            val hostname = InetAddress.getLocalHost().hostName
            call.respondText(hostname)
        }
    }
}
