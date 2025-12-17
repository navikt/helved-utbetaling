package libs.kafka

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.kafka.KafkaStreamsMetrics
import libs.kafka.processor.LogConsumeTopicProcessor
import libs.kafka.stream.ConsumedStream
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KafkaStreams.State.*
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import kotlin.time.Duration
import kotlin.time.toJavaDuration

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
    private val restoreListener = RestoreListener()

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
    internalStreams.setGlobalStateRestoreListener(restoreListener)
    internalStreams.start()
    KafkaStreamsMetrics(internalStreams).bindTo(registry)
}

override fun ready(): Boolean { 
    val state = internalStreams.state()
    return initiallyStarted && state == RUNNING && !restoreListener.isRestoring()
}
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

    fun <K: Any, V : Any> consume(topic: Topic<K, V>, includeTombstones: Boolean = false): ConsumedStream<K, V> {
        val stream = builder
            .stream(topic.name, topic.consumed())
            .process({ LogConsumeTopicProcessor<K, V>(topic) })

        if (includeTombstones) return ConsumedStream(stream)
        return ConsumedStream(stream.skipTombstone(topic) )
    }

    fun <K: Any, V : Any> mock(topic: Topic<K, V>, includeTombstones: Boolean = false): ConsumedStream<K, V> {
        val stream = builder
            .stream(topic.name, topic.consumed("mock-${topic.name}"))
            .process({ LogConsumeTopicProcessor<K, V>(topic) })

        if (includeTombstones) return ConsumedStream(stream)
        return ConsumedStream(stream.skipTombstone(topic) )
    }

    fun <K: Any, V : Any> consume(table: Table<K, V>, materializeWithTrace: Boolean = false): KTable<K, V> {
        return builder
            .stream(table.sourceTopicName, table.sourceTopic.consumed())
            .process({ LogConsumeTopicProcessor<K, V?>(table.sourceTopic) })
            .toKTable(table, materializeWithTrace)
    }

    fun <K: Any, V: Any> globalKTable(
        table: Table<K, V>,
        retention: Duration? = null,
        materializeWithTrace: Boolean = false,
    ): GlobalKTable<K, V> {
        if (retention == null) {
            return GlobalKTable(table, builder.globalTable(
                table.sourceTopicName,
                when (materializeWithTrace) {
                    true -> materializedWithTrace(table)
                    false -> materialized(table)
                }
            ))
        }
        return GlobalKTable(table, builder.globalTable(
            table.sourceTopicName,
            when (materializeWithTrace) {
                true -> materializedWithTrace(table).withRetention(retention.toJavaDuration())
                false -> materialized(table).withRetention(retention.toJavaDuration())
            }
        ))
    }

    fun registerInternalTopology(stream: Streams) {
        stream.registerInternalTopology(builder.build())
    }

    fun intercept(block: StreamsBuilder.() -> Unit) = builder.block()
}

fun topology(init: Topology.() -> Unit): Topology = Topology().apply(init)

