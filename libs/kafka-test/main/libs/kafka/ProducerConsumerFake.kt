package libs.kafka

import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy

class KafkaProducerFake<K: Any, V>(
    topic: Topic<K, V & Any>,
    private val producer: MockProducer<K, V> = MockProducer(true, topic.serdes.key.serializer(), topic.serdes.value.serializer()),
): KafkaProducer<K, V>(topic, producer) {
    fun history(): List<Pair<K, V>> = producer.history().map { it.key() to it.value() }
    fun uncommitted(): List<Pair<K, V>> = producer.uncommittedRecords().map { it.key() to it.value() }
}

class KafkaConsumerFake<K: Any, V>(
    topic: Topic<K, V & Any>,
    resetPolicy: OffsetResetPolicy = OffsetResetPolicy.earliest,  
): KafkaConsumer<K, V>(
    topic,
    MockConsumer(enumValueOf<OffsetResetStrategy>(resetPolicy.name.uppercase()))
)

