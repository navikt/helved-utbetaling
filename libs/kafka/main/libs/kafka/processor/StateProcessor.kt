package libs.kafka.processor

import libs.kafka.StateStoreName
import libs.kafka.Named
import org.apache.kafka.streams.processor.api.ProcessorSupplier

data class StateProcessor<K: Any, V, U, R>(
    val supplier: ProcessorSupplier<K, V, U, R>, 
    val named: Named,
    val storeName: StateStoreName,
)

