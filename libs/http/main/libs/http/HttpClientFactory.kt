package libs.http

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import libs.utils.logger
import libs.utils.secureLog

val httpLog = logger("http")

object HttpClientFactory {
    fun new(
        json: Json,
        logLevel: LogLevel = LogLevel.INFO,
        retries: Int? = 3,
        requestTimeoutMs: Long? = 60_000,
        connectionTimeoutMs: Long? = 30_000,
    ) =
        HttpClient(CIO) {
            install(Logging) {
                logger = ClientLogger(logLevel)
                level = logLevel
            }

            install(ContentNegotiation) {
                json(json)
            }

            retries?.let {
                install(HttpRequestRetry) {
                    retryOnServerErrors(retries)
                    retryOnException(maxRetries = retries, retryOnTimeout = true)
                    exponentialDelay()
                }
            }

            requestTimeoutMs?.let {
                install(HttpTimeout) {
                    requestTimeoutMillis = requestTimeoutMs
                    connectTimeoutMillis = connectionTimeoutMs
                }
            }
        }
}

class ClientLogger(level: LogLevel) : Logger {
    override fun log(message: String) {
        log.info(message)
    }

    private val log = when (level) {

        /**
         * HTTP code, method and url is logged
         */
        LogLevel.INFO, LogLevel.NONE -> httpLog

        /**
         *  HTTP code, method, url, headers request body and response body is logged
         */
        else -> secureLog
    }
}
