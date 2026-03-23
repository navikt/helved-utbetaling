package abetal

import libs.kafka.JacksonSerializer
import libs.kafka.kafkaLog
import models.ApiError
import models.Fagsystem
import models.StatusReply
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.errors.ErrorHandlerContext
import org.apache.kafka.streams.errors.ProcessingExceptionHandler
import org.apache.kafka.streams.processor.api.Record

class StatusOnProcessingErrorHandler : ProcessingExceptionHandler {
    override fun configure(configs: MutableMap<String, *>) {}

    override fun handleError(
        context: ErrorHandlerContext,
        record: Record<*, *>,
        exception: Exception,
    ): ProcessingExceptionHandler.Response {
        val key = runCatching { context.sourceRawKey() }
            .getOrNull()
            ?.let { resolveKey(it) }
            ?: return ProcessingExceptionHandler.Response.resume()

        val error = when (exception) {
            is ApiError -> exception
            else -> ApiError(statusCode = 500, msg = "Feil ved prosessering av melding fra ${context.topic()} (partition=${context.partition()}, offset=${context.offset()}): ${exception.summary()}")
        }

        kafkaLog.error(error.msg, error)

        val fagsystem = fagsystemForTopic(context.topic()) ?: return ProcessingExceptionHandler.Response.resume()

        val statusRecord = statusRecord(
            key = key,
            error = error,
            fagsystem = fagsystem,
        )
        return ProcessingExceptionHandler.Response.resume(listOf(statusRecord))
    }
}

private fun statusRecord(
    key: String,
    error: ApiError,
    fagsystem: Fagsystem,
): ProducerRecord<ByteArray?, ByteArray?> {
    val record = ProducerRecord(
        Topics.status.name,
        StringSerializer().serialize(Topics.status.name, key),
        JacksonSerializer<StatusReply>().serialize(Topics.status.name, StatusReply.err(error))
    )
    record.headers().add(FS_KEY, fagsystem.name.toByteArray(Charsets.UTF_8))
    return record
}

private fun fagsystemForTopic(topic: String): Fagsystem? = when (topic) {
    Topics.dp.name, Topics.dpIntern.name -> Fagsystem.DAGPENGER
    Topics.aap.name, Topics.aapIntern.name -> Fagsystem.AAP
    Topics.ts.name, Topics.tsIntern.name -> Fagsystem.TILLEGGSSTØNADER
    Topics.tp.name -> Fagsystem.TILTAKSPENGER
    Topics.historisk.name, Topics.historiskIntern.name -> Fagsystem.HISTORISK
    else -> when {
        topic.contains("from-${Topics.dp.name}") || topic.contains("dptuple-") -> Fagsystem.DAGPENGER
        topic.contains("from-${Topics.aap.name}") || topic.contains("aaptuple-") -> Fagsystem.AAP
        topic.contains("from-${Topics.ts.name}") || topic.contains("tstuple-") -> Fagsystem.TILLEGGSSTØNADER
        topic.contains("tptuple-") -> Fagsystem.TILTAKSPENGER
        topic.contains("from-${Topics.historisk.name}") || topic.contains("historisktuple-") -> Fagsystem.HISTORISK
        else -> null
    }
}

private fun resolveKey(key: ByteArray?): String? =
    key?.toString(Charsets.UTF_8).takeUnless { it.isNullOrBlank() }

private fun Throwable.summary(): String {
    val cause = message
        ?.replace('\n', ' ')
        ?.trim()
        ?.take(500)
    return if (cause.isNullOrBlank()) javaClass.simpleName else "${javaClass.simpleName}: $cause"
}
