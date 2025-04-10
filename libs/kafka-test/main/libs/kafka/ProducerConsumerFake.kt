package libs.kafka

import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition

class KafkaProducerFake<K: Any, V>(
    private val topic: Topic<K, V & Any>,
    private val producer: MockProducer<K, V> = MockProducer(true, topic.serdes.key.serializer(), topic.serdes.value.serializer()),
): KafkaProducer<K, V>(topic, producer) {
    fun history(): List<Pair<K, V>> = producer.history().map { it.key() to it.value() }
    fun uncommitted(): List<Pair<K, V>> = producer.uncommittedRecords().map { it.key() to it.value() }
}

class KafkaConsumerFake<K: Any, V>(
    private val topic: Topic<K, V & Any>,
    private val resetPolicy: OffsetResetPolicy = OffsetResetPolicy.earliest,  
    private val consumer: MockConsumer<K, V> = MockConsumer(enumValueOf<OffsetResetStrategy>(resetPolicy.name.uppercase()))
): KafkaConsumer<K, V>(topic, consumer) {

    fun populate(key: K, value: V?, partition: Int, offset: Long) {
        val record = ConsumerRecord(topic.name, partition, offset, key, value)
         consumer.addRecord(record)
    }

    fun assign(vararg partition: Int) {
        val partitions = partition.toList().map { TopicPartition(topic.name, it) }
        consumer.assign(partitions)
        consumer.updateBeginningOffsets(partitions.map { p -> p to 0L }.toMap())
    }
}

