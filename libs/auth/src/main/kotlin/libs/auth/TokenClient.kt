package libs.auth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import libs.utils.appLog
import libs.utils.secureLog
import java.net.URL

enum class TokenProvider {
    AzureAd,
    TokenX,
    Maskinporten,
}

class TokenClient(
    private val http: HttpClient,
    private val name: TokenProvider,
    private val cache: Cache = TokenCache()
) {
    suspend fun getAccessToken(
        tokenUrl: URL,
        key: CacheKey,
        body: () -> String,
    ): Token {
        return when (val token = cache.get(key)) {
            null -> update(tokenUrl, key, body())
            else -> token
        }
    }

    private suspend fun update(tokenUrl: URL, key: CacheKey, body: String): Token {
        val res = http.post(tokenUrl) {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(body)
        }

        val token = res.tryInto<Token>()
        cache.add(key, token)
        return token
    }

    private suspend inline fun <reified T> HttpResponse.tryInto(): T {
        when (status.value) {
            in 200..299 -> return body<T>()
            else -> {
                appLog.error("Failed to get token from provider: $name")
                secureLog.error(
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
