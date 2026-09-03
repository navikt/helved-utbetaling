package libs.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import libs.cache.Token
import java.time.Instant

private const val LEEWAY_SEC = 60

sealed interface TokenResponse
data class ProviderRejected(val status: Int) : TokenResponse
data class ProviderUnavailable(val status: Int? = null) : TokenResponse

@Serializable
data class AzureToken(
    val expires_in: Long,
    val access_token: String
) : Token, TokenResponse {
    @Transient
    private val expiry: Instant = Instant.now().plusSeconds(expires_in - LEEWAY_SEC)

    override fun isExpired(): Boolean = Instant.now().isAfter(expiry)

    override fun toString(): String = """
        Expiry: $expires_in 
        Token:  $access_token
    """.trimIndent()
}
