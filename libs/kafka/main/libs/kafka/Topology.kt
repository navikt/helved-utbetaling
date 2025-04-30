package libs.kafka

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.kafka.KafkaStreamsMetrics
import libs.kafka.processor.*
import libs.kafka.processor.Processor.Companion.addProcessor
import libs.kafka.stream.ConsumedStream
import org.apache.kafka.streams.*
import org.apache.kafka.streams.KafkaStreams.State.*
import org.apache.kafka.streams.kstream.*
import org.apache.kafka.streams.state.*

private fun <K: Any, V : Any> nameSupplierFrom(topic: Topic<K, V>): () -> String = { "from-${topic.name}" }

interface Streams : AutoCloseable, KafkaFactory {
    fun connect(topology: Topology, config: StreamsConfig, registry: MeterRegistry)
    fun ready(): Boolean
    fun live(): Boolean
    fun visulize(): TopologyVisulizer
    fun registerInternalTopology(internalTopology: org.apache.kafka.streams.Topology)
    fun <K: Any, V : Any> getStore(store: Store<K, V>): StateStore<K, V>
}

class KafkaStreams : Streams {
    private var initiallyStarted: Boolean = false

    private lateinit var internalStreams: org.apache.kafka.streams.KafkaStreams
    private lateinit var internalTopology: org.apache.kafka.streams.Topology

    override fun connect(
        topology: Topology,
        config: StreamsConfig,
        registry: MeterRegistry, // TODO: remove
    ) {
        topology.registerInternalTopology(this)

        internalStreams = KafkaStreams(internalTopology, config.streamsProperties())
        // KafkaStreamsMetrics(internalStreams).bindTo(registry)
        internalStreams.setUncaughtExceptionHandler(UncaughtHandler())
        internalStreams.setStateListener { state, _ -> if (state == RUNNING) initiallyStarted = true }
        internalStreams.setGlobalStateRestoreListener(RestoreListener())
        internalStreams.start()
    }

    override fun ready(): Boolean = initiallyStarted && internalStreams.state() in listOf(CREATED, REBALANCING, RUNNING)
    override fun live(): Boolean = initiallyStarted && internalStreams.state() != ERROR
    override fun visulize(): TopologyVisulizer = TopologyVisulizer(internalTopology)
    override fun close() = internalStreams.close()

    override fun registerInternalTopology(internalTopology: org.apache.kafka.streams.Topology) {
        this.internalTopology = internalTopology
    }

    override fun <K: Any, V : Any> getStore(store: Store<K, V>): StateStore<K, V> = StateStore(
        internalStreams.store(
            StoreQueryParameters.fromNameAndType<ReadOnlyKeyValueStore<K, V>>(
                store.name,
                QueryableStoreTypes.keyValueStore()
            )
        )
    )
}

class Topology internal constructor() {
    private val builder = StreamsBuilder()

    fun <K: Any, V : Any> consume(topic: Topic<K, V>): ConsumedStream<K, V> {
        val consumed = consumeWithLogging<K, V?>(topic).skipTombstone(topic)
        return ConsumedStream(consumed, nameSupplierFrom(topic))
    }

    fun <K: Any, V : Any> consume(table: Table<K, V>): KTable<K, V> {
        val stream = consumeWithLogging<K, V?>(table.sourceTopic)
        return stream.toKTable(table)
    }

    fun <K: Any, V : Any> consumeRepartitioned(table: Table<K, V>, partitions: Int): KTable<K, V> {
        val internalKTable = consumeWithLogging<K, V?>(table.sourceTopic)
            .repartition(repartitioned(table, partitions))
            .addProcessor(LogProduceTableProcessor(table))
            .toTable(
                Named.`as`("${table.sourceTopicName}-to-table"),
                materialized(table)
            )

        return KTable(table, internalKTable)
    }

    /**
     * The topology does not allow duplicate named nodes.
     * Somethimes it is necessary to consume the same topic again for mocking external responses.
     */
    fun <K: Any, V : Any> consumeForMock(topic: Topic<K, V>, namedPrefix: String = "mock"): ConsumedStream<K, V> {
        val consumed = consumeWithLogging(topic, namedPrefix).skipTombstone(topic, namedPrefix)
        val prefixedNamedSupplier = { "$namedPrefix-${nameSupplierFrom(topic).invoke()}" }
        return ConsumedStream(consumed, prefixedNamedSupplier)
    }

    fun <K: Any, V : Any> consume(
        topic: Topic<K, V>,
        onEach: (key: K, value: V?, metadata: ProcessorMetadata) -> Unit,
    ): ConsumedStream<K, V> {
        val stream = consumeWithLogging<K, V?>(topic)
        stream.addProcessor(MetadataProcessor(topic.name)).foreach { _, (kv, metadata) ->
            onEach(kv.key, kv.value, metadata)
        }
        return ConsumedStream(stream.skipTombstone(topic), nameSupplierFrom(topic))
    }

    fun registerInternalTopology(stream: Streams) {
        stream.registerInternalTopology(builder.build())
    }

    fun intercept(block: StreamsBuilder.() -> Unit) = builder.block()

    private fun <K: Any, V> consumeWithLogging(topic: Topic<K, V & Any>): KStream<K, V> = consumeWithLogging(topic, "")

    private fun <K: Any, V> consumeWithLogging(topic: Topic<K, V & Any>, namedSuffix: String): KStream<K, V> {
        val consumeInternal = topic.consumed("consume-${topic.name}$namedSuffix")
        val consumeLogger = LogConsumeTopicProcessor<K, V>(topic, namedSuffix)
        return builder.stream(topic.name, consumeInternal).addProcessor(consumeLogger)
    }
}

fun topology(init: Topology.() -> Unit): Topology = Topology().apply(init)

