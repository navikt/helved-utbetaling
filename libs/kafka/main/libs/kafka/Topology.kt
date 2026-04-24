package libs.kafka

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.kafka.KafkaStreamsMetrics
import kotlin.concurrent.thread
import libs.kafka.processor.LogConsumeTopicProcessor
import libs.kafka.stream.ConsumedStream
import libs.utils.appLog
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

    /**
     * Connect and start Kafka Streams, pausing processing until [awaitReady] returns true.
     * Monitors [awaitReady] continuously — pauses when false, resumes when true.
     */
    fun start(
        topology: Topology,
        config: StreamsConfig,
        registry: MeterRegistry,
        awaitReady: () -> Boolean,
    )
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

override fun start(
    topology: Topology,
    config: StreamsConfig,
    registry: MeterRegistry,
    awaitReady: () -> Boolean,
) {
    connect(topology, config, registry)
    internalStreams.pause()
    appLog.info("Kafka Streams started in paused state, awaiting dependency readiness")

    thread(isDaemon = true, name = "kafka-dependency-monitor") {
        var paused = true
        var freshStart = true
        var healthySince = 0L

        while (!Thread.currentThread().isInterrupted) {
            val dependencyReady = runCatching { awaitReady() }.getOrDefault(false)
            when {
                paused && dependencyReady -> {
                    if (freshStart) {
                        internalStreams.resume()
                        paused = false
                        freshStart = false
                        appLog.info("Dependency ready, resuming Kafka Streams")
                    } else {
                        if (healthySince == 0L) {
                            healthySince = System.currentTimeMillis()
                            appLog.info("Dependency recovering, waiting 30s before resuming Kafka Streams")
                        }
                        if (System.currentTimeMillis() - healthySince >= 30_000) {
                            internalStreams.resume()
                            paused = false
                            healthySince = 0L
                            appLog.info("Dependency stable for 30s, resuming Kafka Streams")
                        }
                    }
                }
                paused && !dependencyReady -> {
                    freshStart = false
                    healthySince = 0L
                }
                !paused && !dependencyReady -> {
                    internalStreams.pause()
                    paused = true
                    healthySince = 0L
                    appLog.warn("Dependency unavailable, pausing Kafka Streams")
                }
            }
            Thread.sleep(3000)
        }
    }
}

override fun ready(): Boolean { 
    val state = internalStreams.state()
    return initiallyStarted && state == RUNNING && !internalStreams.isPaused && !restoreListener.isRestoring()
}
override fun live(): Boolean = when (internalStreams.state()) {
    ERROR, PENDING_ERROR, PENDING_SHUTDOWN, NOT_RUNNING -> false
    else -> true // CREATED, REBALANCING, RUNNING — JVM is healthy; restart will not help
}
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

    fun <K: Any, V : Any> consume(table: Table<K, V>): KTable<K, V> {
        return builder
            .stream(table.sourceTopicName, table.sourceTopic.consumed())
            .process({ LogConsumeTopicProcessor<K, V?>(table.sourceTopic) })
            .toKTable(table)
    }

    fun <K: Any, V: Any> globalKTable(
        table: Table<K, V>,
        retention: Duration? = null,
    ): GlobalKTable<K, V> {
        if (retention == null) {
            return GlobalKTable(table, builder.globalTable(
                table.sourceTopicName,
                materialized(table)
            ))
        }
        return GlobalKTable(table, builder.globalTable(
            table.sourceTopicName,
            materialized(table).withRetention(retention.toJavaDuration())
        ))
    }

    fun registerInternalTopology(stream: Streams) {
        stream.registerInternalTopology(builder.build())
    }

    fun intercept(block: StreamsBuilder.() -> Unit) = builder.block()
}

fun topology(init: Topology.() -> Unit): Topology = Topology().apply(init)

