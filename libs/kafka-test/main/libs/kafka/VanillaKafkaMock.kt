package libs.kafka

import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.ConcurrentHashMap

class VanillaKafkaMock : Streams, KafkaFactory {

    val config: StreamsConfig = StreamsConfig("", "", null)
    override fun connect(topology: Topology, config: StreamsConfig, registry: MeterRegistry) {}
    override fun ready() = true
    override fun live() = true
    override fun visulize(): TopologyVisulizer = TODO("Not supported")
    override fun registerInternalTopology(internalTopology: org.apache.kafka.streams.Topology) {}
    override fun <K : Any, V : Any> getStore(store: Store<K, V>): StateStore<K, V> = TODO("Not supported")
    override fun close(gracefulMillis: Long) = close()

    private val producers = ConcurrentHashMap<String, KafkaProducerFake<*, *>>()
    private val consumers = ConcurrentHashMap<String, KafkaConsumerFake<*, *>>()

    fun reset() {
        producers.values.forEach { it.clear() }
        consumers.clear()
    }

    override fun close() {
        producers.clear()
        consumers.clear()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <K : Any, V> createProducer(
        config: StreamsConfig,
        topic: Topic<K, V & Any>
    ): KafkaProducer<K, V> {
        return producers.getOrPut(topic.name) {
            KafkaProducerFake(topic)
        } as KafkaProducer<K, V>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <K : Any, V> createConsumer(
        config: StreamsConfig,
        topic: Topic<K, V & Any>,
        resetPolicy: OffsetResetPolicy,
        maxProcessingTimeMs: Int,
        groupId: Int
    ): KafkaConsumer<K, V> {
        return consumers.getOrPut(topic.name) { KafkaConsumerFake(topic) } as KafkaConsumerFake<K, V>
    }


    @Suppress("UNCHECKED_CAST")
    fun <K : Any, V> getProducer(topic: Topic<K, V & Any>): KafkaProducerFake<K, V> {
        return createProducer(config, topic) as KafkaProducerFake<K, V>
    }

    @Suppress("UNCHECKED_CAST")
    fun <K : Any, V> getConsumer(topic: Topic<K, V & Any>): KafkaConsumerFake<K, V> {
        return createConsumer(config, topic) as KafkaConsumerFake<K, V>
    }
}