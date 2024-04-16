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
import libs.utils.appLog
import libs.utils.secureLog

object HttpClientFactory {

    fun new(
        logLevel: LogLevel = LogLevel.INFO,
        configure: (ObjectMapper.() -> Unit) = { defaults() }
    ): HttpClient = HttpClient(CIO) {

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 5_000
        }

        install(HttpRequestRetry)

        install(Logging) {
            logger = ClientLogger(logLevel)
            level = logLevel
        }

        install(ContentNegotiation) {
            jackson {
                this.configure()
            }
        }
    }

    private fun ObjectMapper.defaults() {
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        registerModule(JavaTimeModule())
    }
}

internal class ClientLogger(level: LogLevel) : Logger {
    override fun log(message: String) {
        log.info(message)
    }

    private val log = when (level) {

        /**
         * HTTP code, method and url is logged
         */
        LogLevel.INFO, LogLevel.NONE -> appLog

        /**
         *  HTTP code, method, url, headers request body and response body is logged
         */
        else -> secureLog
    }
}
