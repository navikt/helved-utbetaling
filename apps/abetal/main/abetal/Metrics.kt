package abetal

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class Metrics(private val registry: MeterRegistry) {

    companion object {
        const val PAYMENT_PROCESSED_TOTAL = "helved_abetal_payment_processed_total"
        const val OPPDRAG_SEND_SECONDS = "helved_abetal_oppdrag_send_seconds"
        const val KVITTERING_WAIT_SECONDS = "helved_abetal_kvittering_wait_seconds"
        const val PROCESSING_ERROR_TOTAL = "helved_abetal_processing_error_total"
        const val STATE_STORE_SIZE = "helved_abetal_state_store_size"

        const val OPPDRAG_SENT_AT_HEADER = "abetal-oppdrag-sent-at-ms"
    }

    enum class PaymentResult(val value: String) {
        OK("ok"),
        ERROR("error")
    }

    enum class ProcessingErrorKind(val value: String) {
        VALIDATION("validation"),
        TOPOLOGY("topology"),
        OTHER("other")
    }

    enum class StateStoreMetric(val storeName: String) {
        UTBETALINGER(Tables.utbetalinger.stateStoreName),
        PENDING_UTBETALINGER(Tables.pendingUtbetalinger.stateStoreName)
    }

    private val paymentProcessedCounters = PaymentResult.entries.associateWith { result ->
        Counter.builder(PAYMENT_PROCESSED_TOTAL)
            .tag("result", result.value)
            .register(registry)
    }

    private val oppdragSendTimer = Timer.builder(OPPDRAG_SEND_SECONDS)
        .register(registry)

    private val kvitteringWaitTimer = Timer.builder(KVITTERING_WAIT_SECONDS)
        .register(registry)

    private val processingErrorCounters = ProcessingErrorKind.entries.associateWith { kind ->
        Counter.builder(PROCESSING_ERROR_TOTAL)
            .tag("kind", kind.value)
            .register(registry)
    }

    private val stateStoreSizes = StateStoreMetric.entries.associateWith { store ->
        AtomicInteger(0).also { size ->
            Gauge.builder(STATE_STORE_SIZE, size) { it.get().toDouble() }
                .tag("store", store.storeName)
                .register(registry)
        }
    }

    private val initializedStores = ConcurrentHashMap.newKeySet<StateStoreMetric>()

    fun paymentProcessed(result: PaymentResult) {
        paymentProcessedCounters.getValue(result).increment()
    }

    fun processingError(kind: ProcessingErrorKind) {
        processingErrorCounters.getValue(kind).increment()
    }

    fun oppdragSend(startedAtMs: Long) {
        record(oppdragSendTimer, startedAtMs)
    }

    fun kvitteringWait(sentAtMs: Long) {
        record(kvitteringWaitTimer, sentAtMs)
    }

    fun stateStoreWrite(store: StateStoreMetric, isNewEntry: Boolean, currentSize: Int) {
        val size = stateStoreSizes.getValue(store)
        if (initializedStores.add(store)) {
            size.set(currentSize + if (isNewEntry) 1 else 0)
            return
        }
        if (isNewEntry) size.incrementAndGet()
    }

    private fun record(timer: Timer, startedAtMs: Long) {
        val durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0)
        timer.record(Duration.ofMillis(durationMs))
    }
}
