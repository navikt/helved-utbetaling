package libs.auth

import java.util.Base64
import kotlinx.serialization.json.*
import java.net.URL
import java.net.http.HttpClient
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class Jwt(
    val header: Header,
    val claims: Claims,
    val signatureBytes: ByteArray,
    val raw: String,
) {
    data class Header(val kid: String, val alg: String)
    data class Claims(val map: Map<String, Any?>) {
        fun audience(): List<String> = (map["aud"] as? List<*>)?.filterIsInstance<String>() ?: listOfNotNull(map["aud"] as? String)
        fun issuer(): String? = map["iss"] as? String
        fun expiresAt(): Long? = (map["exp"] as? Number)?.toLong()
        fun notBefore(): Long? = (map["nbf"] as? Number)?.toLong()
        fun issuedAt(): Long? = (map["iat"] as? Number)?.toLong()
        fun claim(name: String): String? = map[name]?.toString()
        fun hasClaim(name: String): Boolean = map.containsKey(name)
    }

    val signedContent: ByteArray get() = raw.substringBeforeLast('.').toByteArray()

    companion object {
        private val decoder = Base64.getUrlDecoder()
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(token: String): Jwt {
            val parts = token.split('.')
            require(parts.size == 3) { "Invalid JWT format" }
            val header = parseHeader(token)
            val claims = parseClaims(parts[1])
            val signatureBytes = decoder.decode(parts[2])
            return Jwt(header, claims, signatureBytes, token)
        }

        private fun parseHeader(token: String): Header {
            val headerSegment = token.substringBefore('.')
            val bytes = decoder.decode(headerSegment)
            val obj = json.parseToJsonElement(String(bytes)).jsonObject
            return Header(
                kid = obj["kid"]?.jsonPrimitive?.content ?: error("missing kid"),
                alg = obj["alg"]?.jsonPrimitive?.content ?: error("missing alg"),
            )
        }

        private fun parseClaims(segment: String): Claims {
            val bytes = decoder.decode(segment)
            val obj = json.parseToJsonElement(String(bytes)).jsonObject
            val map = obj.entries.associate { (k, v) ->
                k to when (v) {
                    is JsonPrimitive -> when {
                        v.isString -> v.content
                        v.content.toLongOrNull() != null -> v.long
                        v.content.toBooleanStrictOrNull() != null -> v.boolean
                        else -> v.content
                    }
                    is JsonArray -> v.map { it.jsonPrimitive.content }
                    else -> v.toString()
                }
            }
            return Claims(map)
        }
    }
}

class JwksClient(private val jwksUrl: URL, private val http: HttpClient) {
    private val keys = ConcurrentHashMap<String, RSAPublicKey>()
    private var lastFetch: Instant = Instant.MIN

    fun getKey(kid: String): RSAPublicKey {
        keys[kid]?.let { return it }
        if (Duration.between(lastFetch, Instant.now()) > Duration.ofMinutes(1)) refresh()
        return keys[kid] ?: error("Unknown kid: $kid")
    }

    private fun refresh() {
        val response = http.send(
            java.net.http.HttpRequest.newBuilder(jwksUrl.toURI()).GET().build(),
            java.net.http.HttpResponse.BodyHandlers.ofString(),
        )
        val jwks = Json.parseToJsonElement(response.body()).jsonObject
        val keyArray = jwks["keys"]?.jsonArray ?: error("No keys in JWKS")
        for (key in keyArray) {
            val obj = key.jsonObject
            val kid = obj["kid"]?.jsonPrimitive?.content ?: continue
            val n = obj["n"]?.jsonPrimitive?.content ?: continue
            val e = obj["e"]?.jsonPrimitive?.content ?: continue
            keys[kid] = buildRsaKey(n, e)
        }
        lastFetch = Instant.now()
    }

    private fun buildRsaKey(n: String, e: String): RSAPublicKey {
        val modulus = java.math.BigInteger(1, Base64.getUrlDecoder().decode(n))
        val exponent = java.math.BigInteger(1, Base64.getUrlDecoder().decode(e))
        val spec = java.security.spec.RSAPublicKeySpec(modulus, exponent)
        return java.security.KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
    }
}

class JwtVerifier(private val jwks: JwksClient, private val config: TokenConfig) {
    fun verify(token: String): Jwt {
        val jwt = Jwt.parse(token)
        val key = jwks.getKey(jwt.header.kid)
        val sig = java.security.Signature.getInstance("SHA256withRSA")
        sig.initVerify(key)
        sig.update(jwt.signedContent)
        require(sig.verify(jwt.signatureBytes)) { "Invalid signature" }
        validateClaims(jwt.claims)
        return jwt
    }

    private fun validateClaims(claims: Jwt.Claims) {
        val now = Instant.now().epochSecond
        val leeway = 30L
        require(config.clientId in claims.audience()) { "clientId not in audience" }
        claims.expiresAt()?.let { require(it + leeway > now) { "Token expired" } }
        claims.notBefore()?.let { require(it - leeway <= now) { "Token not yet valid" } }
        claims.issuedAt()?.let { exp -> claims.expiresAt()?.let { require(exp <= it) { "iat after exp" } }}
        require(claims.issuer() == config.issuer) { "Invalid issures" }
    }
}

