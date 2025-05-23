package libs.kafka

import io.micrometer.core.instrument.MeterRegistry
// import io.micrometer.core.instrument.binder.kafka.KafkaTestMetrics
import org.apache.kafka.streams.TopologyTestDriver
import kotlin.time.Duration
import kotlin.time.toJavaDuration

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
        // KafkaTestMetrics(registry, internalStreams::metrics)
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

    fun append(topology: Topology, block: Topology.() -> Unit): Topology {
        return topology.apply(block)
    }

    fun advanceWallClockTime(duration: Duration) {
        internalStreams.advanceWallClockTime(duration.toJavaDuration())
    }


    fun <K: Any, V : Any> testTopic(topic: Topic<K, V>): TestTopic.InputOutput<K, V> {
        return TestTopic.InputOutput(
            internalStreams.createInputTopic(
                topic.name,
                topic.serdes.key.serializer(),
                topic.serdes.value.serializer()
            ),
            internalStreams.createOutputTopic(
                topic.name,
                topic.serdes.key.deserializer(),
                topic.serdes.value.deserializer()
            )
        )
    }

    fun <K: Any, V : Any> testInputTopic(topic: Topic<K, V>): TestTopic.Input<K, V> {
        return TestTopic.Input(
            internalStreams.createInputTopic(
                topic.name,
                topic.serdes.key.serializer(),
                topic.serdes.value.serializer()
            ),
        )
    }

    fun <K: Any, V : Any> testOutputTopic(topic: Topic<K, V>): TestTopic.Output<K, V> {
        return TestTopic.Output(
            internalStreams.createOutputTopic(
                topic.name,
                topic.serdes.key.deserializer(),
                topic.serdes.value.deserializer()
            )
        )
    }

    private val producers = mutableMapOf<Topic<*, *>, KafkaProducer<*, *>>()

    @Suppress("UNCHECKED_CAST")
    override fun <K: Any, V> createProducer(
        config: StreamsConfig,
        topic: Topic<K, V & Any>,
    ): KafkaProducer <K, V> {
        return producers.getOrPut(topic) { 
            KafkaProducerFake(topic) 
        } as KafkaProducerFake<K, V>
    }

    @Suppress("UNCHECKED_CAST")
    fun <K: Any, V> getProducer(topic: Topic<K, V & Any>): KafkaProducerFake<K, V> {
        return producers[topic] as KafkaProducerFake<K, V>
    }

    override fun <K: Any, V> createConsumer(
        config: StreamsConfig,
        topic: Topic<K, V & Any>,
        resetPolicy: OffsetResetPolicy,  
        maxProcessingTimeMs: Int,
        groupId: Int,
    ): KafkaConsumer<K, V> {
        return KafkaConsumerFake(topic, resetPolicy)
    }

    override fun close() {
        producers.clear()
        internalStreams.close()
    }
}
