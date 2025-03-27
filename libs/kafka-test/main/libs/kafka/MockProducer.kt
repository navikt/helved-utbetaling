package libs.kafka

import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import org.apache.kafka.clients.producer.MockProducer as ApacheMockProducer;

class MockProducer<K : Any, V : Any>(
    topic: Topic<K, V>,
    private val testTopic: TestTopic<K, V>,
) : ApacheMockProducer<K, V>(true, topic.serdes.key.serializer(), topic.serdes.value.serializer()) {
    private val producedKeys = mutableSetOf<K>()

    override fun send(record: ProducerRecord<K, V>, callback: Callback): Future<RecordMetadata> {
        // val metadata = RecordMetadata(TopicPartition(record.topic(), 0), 0L, 0, System.currentTimeMillis(), 0, 0)
        // if (producedKeys.contains(record.key())) {
        //     return CompletableFuture.completedFuture(metadata)
        // }
        // producedKeys.add(record.key())
        // testTopic.produce(record.key(), record::value)
        // callback.onCompletion(metadata, null)
        // return CompletableFuture.completedFuture(metadata)
        return super.send(record, callback)
    }
}
