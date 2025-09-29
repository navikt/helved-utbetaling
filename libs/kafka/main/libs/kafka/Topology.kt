package libs.kafka

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.kafka.KafkaStreamsMetrics
import libs.kafka.processor.LogConsumeTopicProcessor
import libs.kafka.processor.MetadataProcessor
import libs.kafka.processor.Processor.Companion.addProcessor
import libs.kafka.processor.ProcessorMetadata
import libs.kafka.stream.ConsumedStream
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KafkaStreams.State.*
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore

private fun <K: Any, V : Any> nameSupplierFrom(topic: Topic<K, V>): () -> String = { "from-${topic.name}" }

interface Streams : AutoCloseable, KafkaFactory {
    fun connect(topology: Topology, config: StreamsConfig, registry: MeterRegistry)
    fun ready(): Boolean
    fun live(): Boolean
    fun visulize(): TopologyVisulizer
    fun registerInternalTopology(internalTopology: org.apache.kafka.streams.Topology)
    fun <K: Any, V : Any> getStore(store: Store<K, V>): StateStore<K, V>
    fun close(gracefulMillis: Long)
}

class KafkaStreams : Streams {
    private var initiallyStarted: Boolean = false

    private lateinit var internalStreams: org.apache.kafka.streams.KafkaStreams
    private lateinit var internalTopology: org.apache.kafka.streams.Topology

    override fun connect(
        topology: Topology,
        config: StreamsConfig,
        registry: MeterRegistry,
    ) {
    topology.registerInternalTopology(this)

    internalStreams = KafkaStreams(internalTopology, config.streamsProperties())
    internalStreams.setUncaughtExceptionHandler(UncaughtHandler())
    internalStreams.setStateListener { state, _ -> if (state == RUNNING) initiallyStarted = true }
    internalStreams.setGlobalStateRestoreListener(RestoreListener())
    internalStreams.start()
    KafkaStreamsMetrics(internalStreams).bindTo(registry)
}

override fun ready(): Boolean = initiallyStarted && internalStreams.state() in listOf(CREATED, REBALANCING, RUNNING)
override fun live(): Boolean = initiallyStarted && internalStreams.state() != ERROR
override fun visulize(): TopologyVisulizer = TopologyVisulizer(internalTopology)
override fun close() = close(45_000)
override fun close(gracefulMillis: Long) {
    if(!internalStreams.close(java.time.Duration.ofMillis(gracefulMillis))) {
        kafkaLog.error("Gracefully waited ${gracefulMillis}ms for streams to close all its thread, but was forced to shutdown before it completed.")
    }
}

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

object Names {
    private val names: MutableSet<Named> = mutableSetOf()

    internal fun register(name: Named) {
        if (names.contains(name)) error("$name already registered")
        names.add(name)
    }

    fun clear() = names.clear()
}

@JvmInline
value class Named(private val value: String) {
    constructor(num: Int): this("$num")

    init {
        Names.register(this)
    }

    fun into() = org.apache.kafka.streams.kstream.Named.`as`(value)

    operator fun plus(name: String): Named = Named("$value-$name")

    override fun toString() = value
}

class Topology {
    private val builder = StreamsBuilder()

    fun <K: Any, V : Any> consume(topic: Topic<K, V>): ConsumedStream<K, V> {
        val stream = builder
            .stream(topic.name, topic.consumed())
            .addProcessor(LogConsumeTopicProcessor<K, V>(topic))
            .skipTombstone(topic) 
        return ConsumedStream(stream)
    }

    fun <K: Any, V : Any> consume(table: Table<K, V>): KTable<K, V> {
        return builder
            .stream(table.sourceTopicName, table.sourceTopic.consumed())
            .addProcessor(LogConsumeTopicProcessor<K, V?>(table.sourceTopic))
            .toKTable(table)
    }

    fun <K: Any, V : Any> forEach(
        topic: Topic<K, V>,
        onEach: (key: K, value: V?, metadata: ProcessorMetadata) -> Unit,
    ) {
        builder
            .stream(topic.name, topic.consumed())
            .addProcessor(LogConsumeTopicProcessor<K, V>(topic))
            .addProcessor(MetadataProcessor())
            .foreach { _, (kv, metadata) -> onEach(kv.key, kv.value, metadata) }
    }

    fun registerInternalTopology(stream: Streams) {
        stream.registerInternalTopology(builder.build())
    }

    fun intercept(block: StreamsBuilder.() -> Unit) = builder.block()
}

fun topology(init: Topology.() -> Unit): Topology = Topology().apply(init)

