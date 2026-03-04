package libs.kafka

import libs.utils.secureLog
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.streams.errors.*
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse as StreamHandler

class ReplaceThread(message: Any) : RuntimeException(message.toString())

/**
 * Entry point exception handler (consuming records)
 *
 * Exceptions during deserialization, networks issues etc.
 */
class ConsumeAgainHandler : DeserializationExceptionHandler {
    override fun configure(configs: MutableMap<String, *>) {}

    override fun handleError(
        context: ErrorHandlerContext,
        record: ConsumerRecord<ByteArray, ByteArray>,
        exception: Exception
    ): DeserializationExceptionHandler.Response {
        val msg = """
               Feil ved deserializing, forsøker igjen.
               Topic: ${record.topic()}
               Partition: ${record.partition()}
               Offset: ${record.offset()}
               TaskId: ${context.taskId()}
        """.trimIndent()

        kafkaLog.warn(msg)
        secureLog.warn(msg, exception)

        return DeserializationExceptionHandler.Response.fail()
    }
}

class ConsumeNextHandler : DeserializationExceptionHandler {
    override fun configure(configs: MutableMap<String, *>) {}

    override fun handleError(
        context: ErrorHandlerContext,
        record: ConsumerRecord<ByteArray, ByteArray>,
        exception: Exception
    ): DeserializationExceptionHandler.Response {
        val msg = """
               Feil ved deserializing, fortsetter med neste record.
               Topic: ${record.topic()}
               Partition: ${record.partition()}
               Offset: ${record.offset()}
               TaskId: ${context.taskId()}
        """.trimIndent()

        kafkaLog.warn(msg)
        secureLog.warn(msg, exception)

        return DeserializationExceptionHandler.Response.resume()
    }
}

class ProcessAgainHandler : ProcessingExceptionHandler {
    override fun configure(configs: MutableMap<String, *>) {}

    override fun handleError(
        c: ErrorHandlerContext,
        r: org.apache.kafka.streams.processor.api.Record<*, *>,
        e: java.lang.Exception
    ): ProcessingExceptionHandler.Response {
        kafkaLog.error("Feil ved prosessering i topologien, forsøker igjen.")
        secureLog.error("Feil ved prosessering i topologien, forsøker igjen.", e)
        return ProcessingExceptionHandler.Response.fail()
    }
}

class ProcessNextHandler : ProcessingExceptionHandler {
    override fun configure(configs: MutableMap<String, *>) {}

    override fun handleError(
        c: ErrorHandlerContext,
        r: org.apache.kafka.streams.processor.api.Record<*, *>,
        e: java.lang.Exception
    ): ProcessingExceptionHandler.Response {
        kafkaLog.error("Feil ved prosessering i topologien, fortsetter med neste record.")
        secureLog.error("Feil ved prosessering i topologien, fortsetter med neste record.", e)
        return ProcessingExceptionHandler.Response.resume()
    }
}

/**
 * Processing exception handling (process records in the user code)
 *
 * Exceptions not handled by Kafka Streams
 * Three options:
 *  1. replace thread
 *  2. shutdown indicidual stream instance
 *  3. shutdown all streams instances (with the same application-id
 */
class UncaughtHandler: StreamsUncaughtExceptionHandler {
    override fun handle(exception: Throwable): StreamHandler = logAndShutdownClient(exception)

    private fun logAndShutdownClient(err: Throwable): StreamHandler {
        kafkaLog.error("Uventet feil, logger og avslutter client")
        secureLog.error("Uventet feil, logger og avslutter client", err)
        return StreamHandler.SHUTDOWN_CLIENT
    }
}

/**
 * Exit point exception handler (producing records)
 *
 * Exceptions due to serialization, networking etc.
 */
class ProduceAgainHandler : ProductionExceptionHandler {
    override fun configure(configs: MutableMap<String, *>) {}

    override fun handleError(
        c: ErrorHandlerContext?,
        r: ProducerRecord<ByteArray, ByteArray>?,
        e: java.lang.Exception?
    ): ProductionExceptionHandler.Response {
        kafkaLog.error("Feil ved serializing, forsøker igjen.")
        secureLog.error("Feil ved serializing, forsøker igjen.", e)
        return ProductionExceptionHandler.Response.fail()
    }
}

class ProduceNextHandler : ProductionExceptionHandler {
    override fun configure(configs: MutableMap<String, *>) {}

    override fun handleError(
        c: ErrorHandlerContext?,
        r: ProducerRecord<ByteArray, ByteArray>?,
        e: java.lang.Exception?
    ): ProductionExceptionHandler.Response {
        kafkaLog.error("Feil ved serializing, fortsetter med neste record.")
        secureLog.error("Feil ved serializing, fortsetter med neste record.", e)
        return ProductionExceptionHandler.Response.resume()
    }
}
