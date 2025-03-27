package libs.kafka

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.kafka.KafkaTestMetrics
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
//import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.streams.TopologyTestDriver

class StreamsMock : Streams {
    private lateinit var internalStreams: TopologyTestDriver
    private lateinit var internalTopology: org.apache.kafka.streams.Topology


    override fun connect(topology: Topology, config: StreamsConfig, registry: MeterRegistry) {
        topology.registerInternalTopology(this)

        val testProperties = config.streamsProperties()
//            .apply {
//                this[STATE_DIR_CONFIG] = "build/kafka-streams/state"
//                this[MAX_TASK_IDLE_MS_CONFIG] = MAX_TASK_IDLE_MS_DISABLED
//            }

        internalStreams = TopologyTestDriver(internalTopology, testProperties)
        KafkaTestMetrics(registry, internalStreams::metrics)
    }

    override fun ready(): Boolean = true
    override fun live(): Boolean = true

    override fun visulize(): TopologyVisulizer {
        return TopologyVisulizer(internalTopology)
    }

    override fun registerInternalTopology(internalTopology: org.apache.kafka.streams.Topology) {
        this.internalTopology = internalTopology
    }

    override fun <K: Any, V : Any> getStore(store: Store<K, V>): StateStore<K, V> = StateStore(
        internalStreams.getKeyValueStore(store.name)
    )

    fun <K: Any, V : Any> testTopic(topic: Topic<K, V>): TestTopic<K, V> =
        TestTopic(
            input = internalStreams.createInputTopic(
                topic.name,
                topic.serdes.key.serializer(),
                topic.serdes.value.serializer()
            ),
            output = internalStreams.createOutputTopic(
                topic.name,
                topic.serdes.key.deserializer(),
                topic.serdes.value.deserializer()
            )
        )

    private val producers = mutableMapOf<Topic<*, *>, MockProducer<*, *>>()

    @Suppress("UNCHECKED_CAST")
    override fun <K: Any, V : Any> createProducer(
        streamsConfig: StreamsConfig,
        topic: Topic<K, V>,
    ): MockProducer<K, V> {
        return producers.getOrPut(topic) {
            MockProducer(topic, testTopic(topic))
        } as MockProducer<K, V>
    }

    @Suppress("UNCHECKED_CAST")
    fun <K: Any, V : Any> getProducer(topic: Topic<K, V>): MockProducer<K, V> {
        return producers[topic] as MockProducer<K, V>
    }

    override fun <K: Any, V : Any> createConsumer(
        streamsConfig: StreamsConfig,
        topic: Topic<K, V>,
        maxEstimatedProcessingTimeMs: Long,
        groupIdSuffix: Int,
        offsetResetPolicy: OffsetResetPolicy
    ): Consumer<K, V> {
        val resetPolicy = enumValueOf<OffsetResetStrategy>(offsetResetPolicy.name.uppercase())
        return MockConsumer(resetPolicy)
    }

    override fun close() {
        producers.clear()
        internalStreams.close()
    }
}
