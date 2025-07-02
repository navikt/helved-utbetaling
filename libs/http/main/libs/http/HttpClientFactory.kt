package libs.http

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.jackson.*
import libs.utils.logger
import libs.utils.secureLog

val httpLog = logger("http")

object HttpClientFactory {
    fun new(
        logLevel: LogLevel = LogLevel.INFO,
        retries: Int? = 3,
        requestTimeoutMs: Long? = 60_000,
        connectionTimeoutMs: Long? = 30_000,
        json: (ObjectMapper.() -> Unit)? = {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        },
    ) =
        HttpClient(CIO) {
            install(Logging) {
                logger = ClientLogger(logLevel)
                level = logLevel
            }

            json?.let { configure ->
                install(ContentNegotiation) {
                    jackson {
                        configure()
                    }
                }
            }

            retries?.let {
                install(HttpRequestRetry) {
                    retryOnServerErrors(retries)
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
