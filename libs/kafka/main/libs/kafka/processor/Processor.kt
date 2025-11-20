package libs.kafka.processor

import libs.kafka.KeyValue
import libs.kafka.Named
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.Headers
import org.apache.kafka.streams.kstream.KStream
// import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.ProcessorSupplier
import kotlin.jvm.optionals.getOrNull

data class Processor<Kin, Vin, Kout, Vout>(
    val supplier: ProcessorSupplier<Kin, Vin, Kout, Vout>,
)

// class NodeValueProcessor<Kin, Vin, Kout, Vout>(
//     private val node: Node<Kin, Vin, Kout, Vout>,
// ): org.apache.kafka.streams.processor.api.Processor<Kin, Vin, Kout, Vout> {
//     private lateinit var context: ProcessorContext<Kin, Vin>
//
//     override fun init(context: ProcessorContext<Kin, Vin>) {
//         this.context = context
//     }
//
//     override fun process(record: Record<Kin, Vin>) {
//
//         val processedRecord = when (node) {
//             is Node.Header -> processHeader(node, record)
//             is Node.Value -> processValue(node, record)
//             is Node.Key -> processKey(node, record)
//             is Node.Metadata -> processMetadata(node, record)
//         }
//
//         context.forward(processedRecord)
//     }
//
//     private fun processHeader(node: Node.Header<Kin, Vin>, record: Record<Kin, Vin>): Record<Kin, Vin> {
//         val updatedHeaders = node.process(record.headers())
//         return record.withHeaders(updatedHeaders)
//     }
//
//     private fun processValue(node: Node.Value<Kin, Vin, Vout>, record: Record<Kin, Vin>): Record<Kin, Vout> {
//         val updatedValue = node.process(KeyValue(record.key(), record.value()))
//         return record.withValue(updatedValue)
//     }
//
//     private fun processKey(node: Node.Key<Kin, Vin, Kout>, record: Record<Kin, Vin>): Record<Kout, Vin> {
//         val updatedKey = node.process(KeyValue(record.key(), record.value()))
//         return record.withKey(updatedKey)
//     }
//
//     private fun processMetadata(node: Node.Metadata<Kin, Vin>, record: Record<Kin, Vin>): Record<Kin, Vin> {
//         val recordMeta = requireNotNull(context.recordMetadata().getOrNull()) {
//             "Denne er bare null når man bruker punctuators. Det er feil å bruke denne klassen til punctuation."
//         }
//
//         val metadata = ProcessorMetadata(
//             topic = recordMeta.topic() ?: "",
//             partition = recordMeta.partition(),
//             offset = recordMeta.offset(),
//             timestamp = record.timestamp(),
//             systemTimeMs = context.currentSystemTimeMs(),
//             streamTimeMs = context.currentStreamTimeMs(),
//         )
//
//         node.process(metadata = metadata)
//
//         return record
//     }
// }


// abstract class Processor<K: Any, V, U>(private val named: String? = null) : KProcessor<K, V, U> {
//     internal companion object {
//         internal fun <K: Any, V, U> KStream<K, V>.addProcessor(processor: Processor<K, V, U>): KStream<K, U> =
//             when (processor.named) {
//                 null -> processValues({ processor.run { InternalProcessor() } })
//                 else -> processValues({ processor.run { InternalProcessor() } }, Named(processor.named).into())
//             }
//
//         internal fun <K: Any, V, U> KStream<K, V>.addProcessor(processor: HeaderNode): KStream<K, U> =
//             when (processor.named) {
//                 null -> processValues({ processor.run { InternalProcessor() } })
//                 else -> processValues({ processor.run { InternalProcessor() } }, Named(processor.named).into())
//             }
//     }
//
//     private inner class InternalHeaderProcessor: FixedKeyProcessor<K, V, V> {
//         private lateinit var context: FixedKeyProcessorContext<K, V>
//         override fun init(context: FixedKeyProcessorContext<K, V>) {
//             this.context = context
//         }
//
//         override fun process(record: FixedKeyRecord<K, V>) {
//             val headers: Headers = processHeaders(
//                 record.headers()
//             ) 
//
//             context.forward(record.withHeaders(headers))
//         }
//     }
//
//     private inner class InternalProcessor : FixedKeyProcessor<K, V, U> {
//         private lateinit var context: FixedKeyProcessorContext<K, U>
//
//         override fun init(context: FixedKeyProcessorContext<K, U>) {
//             this.context = context
//         }
//
//         override fun process(record: FixedKeyRecord<K, V>) {
//             val recordMeta = requireNotNull(context.recordMetadata().getOrNull()) {
//                 "Denne er bare null når man bruker punctuators. Det er feil å bruke denne klassen til punctuation."
//             }
//
//             val metadata = ProcessorMetadata(
//                 topic = recordMeta.topic() ?: "no topic",
//                 partition = recordMeta.partition(),
//                 offset = recordMeta.offset(),
//                 timestamp = record.timestamp(),
//                 systemTimeMs = context.currentSystemTimeMs(),
//                 streamTimeMs = context.currentStreamTimeMs(),
//             )
//
//             val headers: Headers = processHeaders(
//                 record.headers()
//             ) 
//
//             val valueToForward: U = process(
//                 metadata = metadata,
//                 keyValue = KeyValue(record.key(), record.value()),
//             )
//
//             context.forward(record.withHeaders(headers).withValue(valueToForward))
//         }
//     }
// }


