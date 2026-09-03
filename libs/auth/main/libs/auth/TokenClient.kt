package libs.auth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.client.request.forms.*
import kotlinx.coroutines.CancellationException
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
        body: Parameters,
    ): TokenResponse {
        return when (val token = cache.get(key)) {
            null -> update(tokenUrl, key, body)
            else -> token
        }
    }

    private suspend fun update(tokenUrl: URL, key: CacheKey, body: Parameters): TokenResponse {
        return try {
            val res = http.post(tokenUrl) {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(FormDataContent(body))
            }

            val token = res.into()
            if (token is AzureToken) cache.add(key, token)
            token
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            authLog.warn("Failed to get token from provider: $name")
            secureLog.error("Failed to get token from provider: $name", cause)
            ProviderUnavailable()
        }
    }

    private suspend fun HttpResponse.into(): TokenResponse {
        return when (status.value) {
            in 200..299 -> body<AzureToken>()
            HttpStatusCode.TooManyRequests.value -> ProviderUnavailable(status.value)
            in 400..499 -> ProviderRejected(status.value)
            else -> {
                authLog.warn("Failed to get token from provider: $name")
                secureLog.error(
                    """Failed to get token from provider: $name
                    Got HTTP ${status.value} when issuing token from provider: ${request.url}
                    Status: ${status.value}
                    Body: ${bodyAsText()}
                    """.trimIndent(),
                )
                ProviderUnavailable(status.value)
            }
        }
    }
}
