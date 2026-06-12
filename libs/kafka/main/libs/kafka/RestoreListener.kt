package libs.kafka

import org.apache.kafka.common.TopicPartition
import org.apache.kafka.streams.processor.StateRestoreListener
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Logs number of records restored from state store on application startup
 */
internal class RestoreListener : StateRestoreListener {
    private val durationForPartition = hashMapOf<Int, Long>()
    private val activeRestorations = ConcurrentHashMap.newKeySet<TopicPartition>()

    fun isRestoring() = activeRestorations.isNotEmpty()

    override fun onRestoreStart(
        partition: TopicPartition,
        storeName: String,
        startOffset: Long,
        endOffset: Long,
    ) {
        if (endOffset > startOffset) {
            durationForPartition[partition.partition()] = System.currentTimeMillis()
            activeRestorations.add(partition)
            log.info("Restoring $storeName. Need to load ${endOffset - startOffset} records..")
        }
    }

    override fun onRestoreEnd(
        partition: TopicPartition,
        storeName: String,
        totalRestored: Long,
    ) {
        activeRestorations.remove(partition)
        val startMs = durationForPartition.getOrDefault(partition.partition(), Long.MAX_VALUE)
        val duration = (System.currentTimeMillis() - startMs).toDuration(DurationUnit.MILLISECONDS)

        if (totalRestored > 0) {
            log.atInfo()
                .addKeyValue("partition", partition.partition())
                .addKeyValue("topic", partition.topic())
                .addKeyValue("store", storeName)
                .log("recover $totalRestored records on ${partition.topic()}:${partition.partition()} ($duration)")
        }
    }

    override fun onBatchRestored(partition: TopicPartition, storeName: String, endOffset: Long, numRestored: Long) {
        if (endOffset % 10_000 == 0L) {
            log.info("Restoration progress for $storeName: Currently at offset $endOffset")
        }
        // This is very noisy, Don't log anything
    }
}

private val log = LoggerFactory.getLogger("kafka")
