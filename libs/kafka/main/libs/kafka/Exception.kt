package libs.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.streams.errors.*
import org.slf4j.LoggerFactory
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse as StreamHandler
import org.apache.kafka.streams.errors.ProcessingExceptionHandler.ProcessingHandlerResponse as ProcessingHandler;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler.DeserializationHandlerResponse as ConsumeHandler;
import org.apache.kafka.streams.errors.ProductionExceptionHandler.ProductionExceptionHandlerResponse as ProduceHandler; 


private val secureLog = LoggerFactory.getLogger("secureLog")

class ReplaceThread(message: Any) : RuntimeException(message.toString())

/**
 * Entry point exception handler (consuming records)
 *
 * Exceptions during deserialization, networks issues etc.
 */
class ConsumeAgainHandler : DeserializationExceptionHandler {
    override fun configure(configs: MutableMap<String, *>) {}

    override fun handle(context: ErrorHandlerContext, record: ConsumerRecord<ByteArray, ByteArray>, exception: Exception): ConsumeHandler {
        secureLog.warn(
            """
               Exception deserializing record. Retrying...
               Topic: ${record.topic()}
               Partition: ${record.partition()}
               Offset: ${record.offset()}
               TaskId: ${context.taskId()}
            """.trimIndent(),
            exception
        )
        return ConsumeHandler.FAIL
    }
}
class ConsumeNextHandler : DeserializationExceptionHandler {
    override fun configure(configs: MutableMap<String, *>) {}

    override fun handle( context: ErrorHandlerContext, record: ConsumerRecord<ByteArray, ByteArray>, exception: Exception): ConsumeHandler {
        secureLog.warn(
            """
               Exception deserializing record. Reading next record...
               Topic: ${record.topic()}
               Partition: ${record.partition()}
               Offset: ${record.offset()}
               TaskId: ${context.taskId()}
            """.trimIndent(),
            exception
        )
        return ConsumeHandler.CONTINUE
    }
}

class ProcessAgainHandler: ProcessingExceptionHandler {
    override fun configure(configs: MutableMap<String, *>) {}

    override fun handle(
        c: ErrorHandlerContext, 
        r: org.apache.kafka.streams.processor.api.Record<*, *>, 
        e: java.lang.Exception,
    ): ProcessingHandler {
        secureLog.error("Feil ved prosessering av record, logger og leser neste record", e)
        return ProcessingHandler.FAIL
    }
}
class ProcessNextHandler: ProcessingExceptionHandler {
    override fun configure(configs: MutableMap<String, *>) {}

    override fun handle(
        c: ErrorHandlerContext, 
        r: org.apache.kafka.streams.processor.api.Record<*, *>, 
        e: java.lang.Exception,
    ): ProcessingHandler {
        secureLog.error("Feil ved prosessering av record, logger og leser neste record", e)
        return ProcessingHandler.CONTINUE
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

    override fun handle(
        c: ErrorHandlerContext?,
        r: ProducerRecord<ByteArray, ByteArray>?,
        e: java.lang.Exception?
    ): ProduceHandler {
        secureLog.error("Feil i streams, logger og leser neste record", e)
        return ProduceHandler.FAIL
    }
}

class ProduceNextHandler : ProductionExceptionHandler {
    override fun configure(configs: MutableMap<String, *>) {}

    override fun handle(
        c: ErrorHandlerContext?,
        r: ProducerRecord<ByteArray, ByteArray>?,
        e: java.lang.Exception?
    ): ProduceHandler {
        secureLog.error("Feil i streams, logger og leser neste record", e)
        return ProduceHandler.CONTINUE
    }
}
