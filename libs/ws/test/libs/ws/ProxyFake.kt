package libs.ws

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.util.*

internal class ProxyFake : AutoCloseable {
    private val server = embeddedServer(Netty, port = 0, module = Application::proxy).apply { start() }

    val config
        get() = SoapConfig(
            host = URI.create("http://localhost:${server.engine.port}/some/soap/endpoint").toURL(),
            sts = StsConfig(
                host = URI.create("http://localhost:${server.engine.port}").toURL(),
                user = "test",
                pass = "test"
            )
        )

    val sts = StsFake
    val soap = SoapFake

    fun reset() {
        sts.reset()
        soap.reset()
    }

    override fun close() = server.stop(0, 0)
}

internal object StsFake {
    private val defaultResponse = GandalfToken()

    private val defaultRequest: (ApplicationCall) -> Boolean = { call ->
        val auth = call.request.authorization()?.substringAfter("Basic ")
        "test:test" == String(Base64.getDecoder().decode(auth))
    }

    var response: GandalfToken = defaultResponse
    var request = defaultRequest

    fun reset() {
        request = defaultRequest
        response = defaultResponse
    }
}

internal object SoapFake {
    private val defaultResponse = "<xml>success</xml>"
    private val defaultResponseStatus = HttpStatusCode.OK
    private val defaultRequest: (ApplicationCall) -> Boolean = { call ->
        val ct = requireNotNull(call.request.header("Content-Type"))
        val acc = requireNotNull(call.request.header("Accept"))
        ct.contains("text/xml") && acc.contains("*/*")
    }
    var response = defaultResponse
    var responseStatus = defaultResponseStatus
    var request = defaultRequest

    fun reset() {
        request = defaultRequest
        responseStatus = defaultResponseStatus
        response = defaultResponse
    }
}

private fun Application.proxy() {
    install(ContentNegotiation) {
        jackson()
    }

    routing {
        get("/rest/v1/sts/samltoken") {
            when (StsFake.request(call)) {
                true -> call.respond(StsFake.response)
                false -> call.respondText(
                    text = "gandalf did not expect request: ${call.request}",
                    status = HttpStatusCode.InternalServerError
                )
            }
        }

        post("some/soap/endpoint") {
            when (SoapFake.request(call)) {
                true -> call.respondText(SoapFake.response, status = SoapFake.responseStatus)
                false -> call.respondText(
                    text = "soap did not expect request: ${call.request}",
                    status = HttpStatusCode.InternalServerError
                )
            }
        }
    }
}

data class GandalfToken(
    val access_token: String = "very secure".let { Base64.getEncoder().encodeToString(it.toByteArray()) },
    val issued_token_type: String = "urn:ietf:params:oauth:token-type:saml2",
    val token_type: String = "Bearer",
    val expires_in: Long = 3600,
)

val NettyApplicationEngine.port: Int
    get() = runBlocking {
        resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
    }
