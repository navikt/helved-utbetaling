package libs.ws

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.request.header
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class SoapTest {
    companion object {
        private val proxy = ProxyFake()

        @AfterAll
        @JvmStatic
        fun close() = proxy.close()
    }

    @AfterEach
    fun reset() = proxy.reset()

    @Test
    fun `soap fake response can be configured`() {
        val sts = StsClient(proxy.config.sts)
        val soap = SoapClient(proxy.config, sts)

        proxy.soap.response = "sweet"

        val res = runBlocking {
            soap.call("nice", "<xml>hello</xml>")
        }

        assertEquals("sweet", res)
    }

    @Test
    fun `can use proxy-auth`() {
        val sts = StsClient(proxy.config.sts)
        val soap = SoapClient(proxy.config, sts, proxyAuth = suspend {
            "token for proxy"
        })

        proxy.soap.request = {
            it.request.header("X-Proxy-Authorization") == "token for proxy"
        }

        assertDoesNotThrow {
            runBlocking {
                soap.call("action", "yo")
            }
        }
    }

    @Test
    fun `aksepterer 200`() {
        val sts = StsClient(proxy.config.sts)
        val soap = SoapClient(proxy.config, sts)

        proxy.soap.responseStatus = HttpStatusCode.OK

        assertDoesNotThrow {
            runBlocking {
                soap.call("nice", "<xml>hello</xml>")
            }
        }
    }

    @Test
    fun `aksepterer 500`() {
        val sts = StsClient(proxy.config.sts)
        val soap = SoapClient(proxy.config, sts, http = HttpClient(CIO) {
            install(ContentNegotiation) {
                jackson {  }
            }
        })

        proxy.soap.responseStatus = HttpStatusCode.InternalServerError

        assertDoesNotThrow {
            runBlocking {
                soap.call("nice", "<xml>hello</xml>")
            }
        }
    }

    @Test
    fun `kaster feil ved ukjent http statuskode`() {
        val sts = StsClient(proxy.config.sts)
        val soap = SoapClient(proxy.config, sts)

        proxy.soap.responseStatus = HttpStatusCode(418, "I'm a teapot")

        assertThrows<IllegalStateException> {
            runBlocking {
                soap.call("nice", "<xml>hello</xml>")
            }
        }
    }
}