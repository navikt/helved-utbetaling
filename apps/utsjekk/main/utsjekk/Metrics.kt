package utsjekk

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

class Metrics(private val registry: MeterRegistry) {
    private val iverksettingTotal = IverksettingResult.entries.associateWith { result ->
        Counter.builder("helved_utsjekk_iverksetting_total")
            .tag("result", result.tag)
            .register(registry)
    }

    private val iverksettingSeconds = Timer.builder("helved_utsjekk_iverksetting_seconds")
        .register(registry)

    private val dryrunRequestTotal = DryrunEndpoint.entries
        .flatMap { endpoint -> DryrunResult.entries.map { result -> endpoint to result } }
        .associateWith { (endpoint, result) ->
            Counter.builder("helved_utsjekk_dryrun_request_total")
                .tag("endpoint", endpoint.tag)
                .tag("result", result.tag)
                .register(registry)
        }

    private val dryrunSeconds = DryrunEndpoint.entries.associateWith { endpoint ->
        Timer.builder("helved_utsjekk_dryrun_seconds")
            .tag("endpoint", endpoint.tag)
            .register(registry)
    }

    private val lastStatusConsumedAtMillis = AtomicLong(0)

    init {
        Gauge.builder("helved_utsjekk_status_consumer_lag_seconds", lastStatusConsumedAtMillis) { lastConsumed ->
            max((System.currentTimeMillis() - lastConsumed.get()) / 1000.0, 0.0)
        }.register(registry)
    }

    fun startIverksettingTimer(): Timer.Sample = Timer.start(registry)

    fun stopIverksettingTimer(sample: Timer.Sample) {
        sample.stop(iverksettingSeconds)
    }

    fun iverksetting(result: IverksettingResult) {
        iverksettingTotal.getValue(result).increment()
    }

    fun startDryrunTimer(): Timer.Sample = Timer.start(registry)

    fun stopDryrunTimer(endpoint: DryrunEndpoint, sample: Timer.Sample) {
        sample.stop(dryrunSeconds.getValue(endpoint))
    }

    fun dryrun(endpoint: DryrunEndpoint, result: DryrunResult) {
        dryrunRequestTotal.getValue(endpoint to result).increment()
    }

    fun statusConsumed(atMillis: Long = System.currentTimeMillis()) {
        lastStatusConsumedAtMillis.set(atMillis)
    }
}

enum class DryrunEndpoint(val tag: String) {
    V1("v1"),
    V2("v2"),
    V3("v3"),
}

enum class DryrunResult(val tag: String) {
    OK("ok"),
    TIMEOUT("timeout"),
    ERROR("error"),
}

enum class IverksettingResult(val tag: String) {
    OK("ok"),
    ERROR("error"),
}
