package libs.auth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import libs.cache.Cache
import libs.cache.CacheKey
import libs.cache.TokenCache
import libs.utils.logger
import libs.utils.secureLog
import java.net.URL

private val authLog = logger("auth")

object TokenProvider {
    const val AZURE = "Azure AD"
    const val TOKENX = "Token X"
    const val MASKINPORTEN = "Maskinporten"
    const val IDPORTEN = "ID Porten"
}

class TokenClient(
    private val http: HttpClient,
    private val name: String,
    private val cache: Cache<AzureToken> = TokenCache()
) {
    suspend fun getAccessToken(
        tokenUrl: URL,
        key: CacheKey,
        body: () -> String,
    ): AzureToken {
        return when (val token = cache.get(key)) {
            null -> update(tokenUrl, key, body())
            else -> token
        }
    }

    private suspend fun update(tokenUrl: URL, key: CacheKey, body: String): AzureToken {
        val res = http.post(tokenUrl) {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(body)
        }

        val token = res.tryInto<AzureToken>()
        cache.add(key, token)
        return token
    }

    private suspend inline fun <reified T> HttpResponse.tryInto(): T {
        when (status.value) {
            in 200..299 -> return body<T>()
            else -> {
                authLog.warn("Failed to get token from provider: $name")
                secureLog.warn(
                    """
                    Got HTTP ${status.value} when issuing token from provider: ${request.url}
                    Status: ${status.value}
                    Body: ${bodyAsText()}
                    """.trimIndent(),
                )

                error("Failed to get token from provider: ${request.url}")
            }
        }
    }
}
