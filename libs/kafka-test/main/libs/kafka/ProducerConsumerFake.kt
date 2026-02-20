package libs.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.Partitioner
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.Cluster
import java.util.concurrent.Future
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.utils.Utils

class KafkaProducerFake<K: Any, V>(
    private val topic: Topic<K, V & Any>,
    private val producer: MockProducer<K, V> = MockProducer(
        true,                            // automatically execute the callback
        MockPartitioner(3),              // partition strategy
        topic.serdes.key.serializer(),
        topic.serdes.value.serializer(),
    ),
): KafkaProducer<K, V>(topic, producer) {
    fun history(): List<Pair<K, V>> = producer.history().map { it.key() to it.value() }
    fun uncommitted(): List<Pair<K, V>> = producer.uncommittedRecords().map { it.key() to it.value() }

    fun send(record: ProducerRecord<K, V>, callback: Callback): Future<RecordMetadata> {
        // val metadata = RecordMetadata(TopicPartition(record.topic(), 0), 0L, 0, System.currentTimeMillis(), 0, 0)
        // if (producedKeys.contains(record.key())) return CompletableFuture.completedFuture(metadata)
        // producedKeys.add(record.key())
        // testTopic.produce(record.key(), record::value)
        // callback.onCompletion(metadata, null)
        // return CompletableFuture.completedFuture(metadata)
        return producer.send(record, callback)
    }

    fun clear() {
        producer.clear()
    }
}

class KafkaConsumerFake<K: Any, V>(
    private val topic: Topic<K, V & Any>,
    private val resetPolicy: OffsetResetPolicy = OffsetResetPolicy.latest,
    private val consumer: MockConsumer<K, V> = InternalMockConsumer(resetPolicy)
): KafkaConsumer<K, V>(topic, consumer) {

    fun populate(key: K, value: V?, partition: Int, offset: Long) {
        val record = ConsumerRecord(topic.name, partition, offset, key, value)
         consumer.addRecord(record)
    }

    fun assign(vararg partition: Int) {
        val partitions = partition.toList().map { TopicPartition(topic.name, it) }
        consumer.assign(partitions)
        consumer.updateEndOffsets(partitions.map { p -> p to 0L }.toMap())
    }

     class InternalMockConsumer<K, V>(
         resetPolicy: OffsetResetPolicy,
     ) : MockConsumer<K, V>(resetPolicy.name) {
         override fun seekToEnd(partitions: Collection<TopicPartition>) {
             super.updateEndOffsets(partitions.associateWith { 0L })
             super.seekToEnd(partitions)
         }
     }
}

private class MockPartitioner(private val numberOfPartitions: Int = 3): Partitioner {
    override fun close() {}
    override fun configure(p0: Map<String, *>) {}
    override fun partition(topic: String, key: Any, keyBytes: ByteArray, value: Any, valueBytes: ByteArray, p5: Cluster,): Int {
        return Utils.toPositive(Utils.murmur2(keyBytes)) % numberOfPartitions
    }
}
