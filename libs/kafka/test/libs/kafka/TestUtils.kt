package libs.kafka

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import libs.kafka.processor.Processor
import libs.kafka.processor.ProcessorMetadata
import libs.kafka.processor.StateProcessor
import org.apache.kafka.streams.TestInputTopic
import org.apache.kafka.streams.TestOutputTopic
import org.apache.kafka.streams.TopologyTestDriver
import org.apache.kafka.streams.state.TimestampedKeyValueStore
import org.apache.kafka.streams.state.ValueAndTimestamp
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

data class JsonDto(
    val id: Int,
    val data: String,
)

internal object Topics {
    val A = Topic("A", Serdes(StringSerde, StringSerde))
    val B = Topic("B", Serdes(StringSerde, StringSerde))
    val C = Topic("C", Serdes(StringSerde, StringSerde))
    val D = Topic("D", Serdes(StringSerde, StringSerde))
    val E = Topic("E", Serdes(StringSerde, JsonSerde.jackson<JsonDto>()))
}

internal object Tables {
    val B = Table(Topics.B)
    val C = Table(Topics.C)
}

internal object Stores {
    val F = Store("F", string())
}

internal class Mock : Streams {
    private lateinit var internalTopology: org.apache.kafka.streams.Topology
    private lateinit var internalStreams: TopologyTestDriver

    companion object {
        internal fun withTopology(topology: Topology.() -> Unit): Mock =
            Mock().apply {
                connect(
                    topology = Topology().apply(topology),
                    config = StreamsConfig("", "", null),
                    registry = SimpleMeterRegistry()
                )
            }
    }

    override fun connect(topology: Topology, config: StreamsConfig, registry: MeterRegistry) {
        topology.registerInternalTopology(this)
        internalStreams = TopologyTestDriver(internalTopology)
    }

    internal fun <K : Any, V : Any> inputTopic(topic: Topic<K, V>): TestInputTopic<K, V> =
        internalStreams.createInputTopic(topic.name, topic.serdes.key.serializer(), topic.serdes.value.serializer())

    internal fun <K : Any, V : Any> outputTopic(topic: Topic<K, V>): TestOutputTopic<K, V> =
        internalStreams.createOutputTopic(
            topic.name,
            topic.serdes.key.deserializer(),
            topic.serdes.value.deserializer()
        )


    internal fun advanceWallClockTime(duration: Duration) =
        internalStreams.advanceWallClockTime(duration.toJavaDuration())

    override fun ready(): Boolean = true
    override fun live(): Boolean = true
    override fun visulize(): TopologyVisulizer = TopologyVisulizer(internalTopology)
    override fun registerInternalTopology(internalTopology: org.apache.kafka.streams.Topology) {
        this.internalTopology = internalTopology
    }

    override fun <K : Any, V : Any> getStore(store: Store<K, V>): StateStore<K, V> =
        StateStore(internalStreams.getKeyValueStore<K, ValueAndTimestamp<V>>(store.name) as TimestampedKeyValueStore)

    override fun close() = internalStreams.close()
}

internal val Int.ms get() = toDuration(DurationUnit.MILLISECONDS)

internal fun <K : Any, V> TestInputTopic<K, V>.produce(key: K, value: V): TestInputTopic<K, V> =
    pipeInput(key, value).let { this }

internal fun <K : Any, V> TestInputTopic<K, V>.produceTombstone(key: K): TestInputTopic<K, V> =
    pipeInput(key, null).let { this }

class CustomProcessorWithTable(table: KTable<String, String>) :
    StateProcessor<String, String, String, String>("custom-join", table.table.stateStoreName) {
    override fun process(
        metadata: ProcessorMetadata,
        store: TimestampedKeyValueStore<String, String>,
        keyValue: KeyValue<String, String>
    ): String = "${keyValue.value}${store[keyValue.key].value()}"
}

open class CustomProcessor : Processor<String, String, String>("add-v2-prefix") {
    override fun process(metadata: ProcessorMetadata, keyValue: KeyValue<String, String>): String =
        "${keyValue.value}.v2"
}
