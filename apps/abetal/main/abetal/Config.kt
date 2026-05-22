package abetal

import libs.kafka.StreamsConfig
import libs.utils.env
import java.net.URI
import java.net.URL
import java.util.*

data class Config(
    val utsjekk: URL = URI(env("UTSJEKK_HOST", "http://utsjekk")).toURL(),
    val kafka: StreamsConfig = StreamsConfig(
        additionalProperties = Properties().apply {
            // Publiser statusmeldinger når vi ikke klarer å prosessere meldinger
            this[org.apache.kafka.streams.StreamsConfig.PROCESSING_EXCEPTION_HANDLER_CLASS_CONFIG] = StatusOnProcessingErrorHandler::class.java

            // AdminClient trenger lengre tid ved opprettelse av internal topics
            this[org.apache.kafka.streams.StreamsConfig.RETRY_BACKOFF_MS_CONFIG] = 1000
            this[org.apache.kafka.streams.StreamsConfig.RECONNECT_BACKOFF_MS_CONFIG] = 1000
            this[org.apache.kafka.streams.StreamsConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG] = 5000
        }
    ),
)
