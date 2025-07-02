package libs.ws

import io.ktor.server.request.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.math.abs
import kotlin.test.assertNotEquals

class StsTest {
    companion object {
        private val proxy = ProxyFake()

        @AfterAll
        @JvmStatic
        fun close() = proxy.close()
    }

    @AfterEach
    fun reset() = proxy.reset()

    @Test
    fun `token is base64 decoded`() {
        val sts = StsClient(proxy.config.sts)

        val encoded = "very secure".let { Base64.getEncoder().encodeToString(it.toByteArray()) }
        proxy.sts.response = GandalfToken(access_token = encoded)

        val actual = runBlocking {
            sts.samlToken()
        }

        val expected = SamlToken("very secure", LocalDateTime.now().plusSeconds(3600))
        assertEquals(expected.token, actual.token)
        assertIsCloseTo(expected.expirationTime, actual.expirationTime)
    }

    @Test
    fun `expiry is 3600s`() {
        val sts = StsClient(proxy.config.sts)

        proxy.sts.response = GandalfToken(expires_in = 3600)

        val actual = runBlocking {
            sts.samlToken()
        }

        val expected = SamlToken("very secure", LocalDateTime.now().plusSeconds(3600))
        assertIsCloseTo(expected.expirationTime, actual.expirationTime)
    }

    @Test
    fun `issued token type is saml2`() {
        val sts = StsClient(proxy.config.sts)

        proxy.sts.response = GandalfToken(issued_token_type = "urn:ietf:params:oauth:token-type:saml2")

        assertDoesNotThrow {
            runBlocking {
                sts.samlToken()
            }
        }
    }

    @Test
    fun `throws StsException when wrong issued token type`() {
        val sts = StsClient(proxy.config.sts)

        proxy.sts.response = GandalfToken(issued_token_type = "funky:type")
        assertThrows<StsException> {
            runBlocking {
                sts.samlToken()
            }
        }
    }

    @Test
    fun `can cache tokens`() {
        val sts = StsClient(proxy.config.sts)

        val encoded = "other".let { Base64.getEncoder().encodeToString(it.toByteArray()) }

        runBlocking {
            val token = sts.samlToken()
            proxy.sts.response = GandalfToken(access_token = encoded, expires_in = 1)
            val token2 = sts.samlToken()
            assertEquals(token, token2)
        }
    }

    @Test
    fun `can invalidate expired tokens`() {
        val sts = StsClient(proxy.config.sts)

        proxy.sts.response = GandalfToken(expires_in = 1)

        runBlocking {
            val token = sts.samlToken()
            Thread.sleep(1)
            proxy.sts.response = GandalfToken(expires_in = 1)
            val token2 = sts.samlToken()
            assertNotEquals(token, token2)
        }
    }

    @Test
    fun `can use proxy-auth`() {
        val sts = StsClient(proxy.config.sts, proxyAuth = suspend {
            "token for proxy"
        })

        proxy.sts.request = {
            it.request.header("X-Proxy-Authorization") == "token for proxy"
        }

        assertDoesNotThrow {
            runBlocking {
                sts.samlToken()
            }
        }
    }
}

fun assertIsCloseTo(expected: LocalDateTime, actual: LocalDateTime) {
    assertTrue(abs(expected.toEpochSec() - actual.toEpochSec()) < 5)
}

fun LocalDateTime.toEpochSec(): Long = atZone(ZoneId.systemDefault()).toEpochSecond()