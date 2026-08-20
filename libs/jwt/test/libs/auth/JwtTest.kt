package libs.auth

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JwtTest {
    private val generator = JwkGenerator("test-issuer", "test-client")

    @Test
    fun `parse valid JWT`() {
        val token = generator.generate()
        val jwt = Jwt.parse(token)
        assertEquals("localhost-signer", jwt.header.kid)
        assertEquals("RS256", jwt.header.alg)
        assertEquals("test-issuer", jwt.claims.issuer())
        assertTrue("test-client" in jwt.claims.audience())
        assertNotNull(jwt.claims.expiresAt())
    }

    @Test
    fun `parse rejects invalid format`() {
        assertThrows<IllegalArgumentException> {
            Jwt.parse("only.two-parts")
        }
    }

    @Test
    fun `parse rejects four-part token`() {
        assertThrows<IllegalArgumentException> {
            Jwt.parse("a.b.c.d")
        }
    }

    @Test
    fun `Claims accessors`() {
        val token = generator.generate(listOf(Claim("custom_claim", "hello")))
        val claims = Jwt.parse(token).claims
        assertEquals("test-issuer", claims.issuer())
        assertTrue("test-client" in claims.audience())
        assertNotNull(claims.expiresAt())
        assertEquals("hello", claims.claim("custom_claim"))
        assertTrue(claims.hasClaim("custom_claim"))
        assertFalse(claims.hasClaim("nonexistent"))
    }

    @Test
    fun `JwtVerifier validates signature`() {
        val verifier = testVerifier()
        val token = generator.generate()
        val jwt = verifier.verify(token)
        assertEquals("test-issuer", jwt.claims.issuer())
    }

    @Test
    fun `JwtVerifier rejects expired token`() {
        val verifier = testVerifier()
        val expiredToken = createExpiredToken()
        assertThrows<IllegalArgumentException> {
            verifier.verify(expiredToken)
        }
    }

    @Test
    fun `JwtVerifier rejects wrong audience`() {
        val verifier = testVerifier(clientId = "wrong-client")
        val token = generator.generate()
        assertThrows<IllegalArgumentException> {
            verifier.verify(token)
        }
    }

    @Test
    fun `JwtVerifier rejects tampered token`() {
        val verifier = testVerifier()
        val token = generator.generate()
        val parts = token.split('.')
        // Tamper with the payload
        val payload = Base64.getUrlDecoder().decode(parts[1])
        payload[0] = (payload[0] + 1).toByte()
        val tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload) + "." + parts[2]
        assertThrows<IllegalArgumentException> {
            verifier.verify(tampered)
        }
    }

    private fun createExpiredToken(): String {
        val rsaKey = com.nimbusds.jose.jwk.JWKSet.parse(TEST_JWKS)
            .getKeyByKeyId("localhost-signer") as com.nimbusds.jose.jwk.RSAKey
        val header = com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256)
            .keyID("localhost-signer")
            .type(com.nimbusds.jose.JOSEObjectType.JWT)
            .build()
        val claims = com.nimbusds.jwt.JWTClaimsSet.Builder()
            .issuer("test-issuer")
            .audience("test-client")
            .expirationTime(java.util.Date(0)) // epoch = long expired
            .build()
        val signed = com.nimbusds.jwt.SignedJWT(header, claims)
        signed.sign(com.nimbusds.jose.crypto.RSASSASigner(rsaKey.toPrivateKey()))
        return signed.serialize()
    }

    private fun testVerifier(clientId: String = "test-client"): JwtVerifier {
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/jwks") { exchange ->
                val body = TEST_JWKS.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val jwksUrl = URI("http://localhost:${server.address.port}/jwks").toURL()
        val jwksClient = JwksClient(jwksUrl, HttpClient.newHttpClient())
        val config = TokenConfig(clientId = clientId, jwks = jwksUrl, issuer = "test-issuer")
        return JwtVerifier(jwksClient, config)
    }
}
