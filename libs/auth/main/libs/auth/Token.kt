package libs.auth

import java.time.Instant

private const val LEEWAY_SEC = 60

data class Token(
    val expires_in: Long,
    val access_token: String
) {
    private val expiry: Instant = Instant.now().plusSeconds(expires_in - LEEWAY_SEC)

    fun isExpired(): Boolean = Instant.now().isAfter(expiry)

    override fun toString(): String = """
        Expiry: $expires_in 
        Token:  $access_token
    """.trimIndent()
}
