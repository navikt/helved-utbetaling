package libs.auth

import libs.cache.Token
import java.time.Instant

private const val LEEWAY_SEC = 60

data class AzureToken(
    val expires_in: Long,
    val access_token: String
) : Token {
    private val expiry: Instant = Instant.now().plusSeconds(expires_in - LEEWAY_SEC)

    override fun isExpired(): Boolean = Instant.now().isAfter(expiry)

    override fun toString(): String = """
        Expiry: $expires_in 
        Token:  $access_token
    """.trimIndent()
}
