package libs.kafka

import net.logstash.logback.argument.StructuredArguments
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.streams.processor.StateRestoreListener
import org.slf4j.LoggerFactory
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Logs number of records restored from state store on application startup
 */
internal class RestoreListener : StateRestoreListener {
    private val durationForPartition = hashMapOf<Int, Long>()

    override fun onRestoreStart(
        partition: TopicPartition,
        storeName: String,
        startOffset: Long,
        endOffset: Long,
    ) {
        durationForPartition[partition.partition()] = System.currentTimeMillis()
    }

    override fun onRestoreEnd(
        partition: TopicPartition,
        storeName: String,
        totalRestored: Long,
    ) {
        val startMs = durationForPartition.getOrDefault(partition.partition(), Long.MAX_VALUE)
        val duration = (System.currentTimeMillis() - startMs).toDuration(DurationUnit.MILLISECONDS)

        log.info(
            "Recovered #$totalRestored records on partition ${partition.partition()} ($duration)",
            StructuredArguments.kv("partition", partition.partition()),
            StructuredArguments.kv("topic", partition.topic()),
            StructuredArguments.kv("store", storeName),
        )
    }

    override fun onBatchRestored(partition: TopicPartition, storeName: String, endOffset: Long, numRestored: Long) {
        // This is very noisy, Don't log anything
    }
}

private val log = LoggerFactory.getLogger("kafka")
