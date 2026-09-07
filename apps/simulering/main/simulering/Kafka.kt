package simulering

import kotlinx.coroutines.channels.Channel
import libs.kafka.*
import libs.kafka.processor.StateScheduleProcessor
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext
import org.apache.kafka.streams.state.TimestampedKeyValueStoreWithHeaders
import org.apache.kafka.streams.state.ValueAndTimestamp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object Topics {
    val simuleringer = Topic("helved.simuleringer.v1", jaxb<SimulerBeregningRequest>())
    val dryrunAap = Topic("helved.dryrun-aap.v1", json<Simulering>())
    val dryrunDp = Topic("helved.dryrun-dp.v1", json<Simulering>())
    val dryrunTs = Topic("helved.dryrun-ts.v1", json<Simulering>())
    val dryrunTp = Topic("helved.dryrun-tp.v1", json<Simulering>())
    val utbetalingAap = Topic("helved.utbetalinger-aap.v1", json<AapUtbetaling>())
    val utbetalingDp = Topic("helved.utbetalinger-dp.v1", json<DpUtbetaling>())
    val utbetalingTp = Topic("helved.utbetalinger-tp.v1", json<TpUtbetaling>())
    val utbetalingTs = Topic("helved.utbetalinger-ts.v1", json<TsDto>())
}

object Tables {
    val simuleringer = Table(Topics.simuleringer)
    val dryrunAap = Table(Topics.dryrunAap)
    val dryrunDp = Table(Topics.dryrunDp)
    val dryrunTp = Table(Topics.dryrunTp)
    val dryrunTs = Table(Topics.dryrunTs)
}

object Stores {
    val dryrunAap = Store(Tables.dryrunAap)
    val dryrunDp = Store(Tables.dryrunDp)
    val dryrunTp = Store(Tables.dryrunTp)
    val dryrunTs = Store(Tables.dryrunTs)
    val simuleringHeaders = Store("simulering-headers-store", Topics.simuleringer.serdes)
}

data class SimuleringRequest(
    val key: String,
    val request: SimulerBeregningRequest,
    val headers: Map<String, String> = emptyMap(),
)

data class SimuleringBackpressure(
    val key: String,
    val fagsystem: Fagsystem,
    val headers: Map<String, String>,
)

fun Topology.simuleringer(
    channel: Channel<SimuleringRequest>,
    backpressureChannel: Channel<SimuleringBackpressure>,
) {
    globalKTable(Tables.dryrunAap, retention = 1.hours)
    globalKTable(Tables.dryrunDp, retention = 1.hours)
    globalKTable(Tables.dryrunTp, retention = 1.hours)
    globalKTable(Tables.dryrunTs, retention = 1.hours)

    val ktable = consume(Tables.simuleringer, simuleringHeadersProcessor(Stores.simuleringHeaders))
    val scheduler = SimuleringScheduler(ktable, 5.seconds, channel, backpressureChannel)
    ktable.schedule(scheduler)
}

class SimuleringScheduler(
    ktable: KTable<String, SimulerBeregningRequest>,
    interval: Duration,
    private val channel: Channel<SimuleringRequest>,
    private val backpressureChannel: Channel<SimuleringBackpressure>,
    private val evictionTtl: Duration = 2.minutes,
    private val headersStoreDefinition: Store<String, SimulerBeregningRequest>? = Stores.simuleringHeaders,
) : StateScheduleProcessor<String, SimulerBeregningRequest>(
    named = "simulering-scheduler",
    table = ktable,
    interval = interval,
) {
    private var headersStore: TimestampedKeyValueStoreWithHeaders<String, SimulerBeregningRequest>? = null

    override fun additionalStateStores() = listOfNotNull(headersStoreDefinition?.name)

    override fun init(context: FixedKeyProcessorContext<String, SimulerBeregningRequest>) {
        headersStore = headersStoreDefinition?.let { context.getStateStore(it.name) as TimestampedKeyValueStoreWithHeaders<String, SimulerBeregningRequest> }
    }

    override fun schedule(wallClockTime: Long, store: StateStore<String, ValueAndTimestamp<SimulerBeregningRequest>>) {
        store.forEach { entry -> 
            val age = wallClockTime - entry.value.timestamp()
            if (age > evictionTtl.inWholeMilliseconds) {
                store.delete(entry.key)
                headersStore?.delete(entry.key)
                backpressure(entry.key, entry.value.value(), headersStore?.get(entry.key)?.headers()?.toMap() ?: emptyMap())
            } else {
                store.delete(entry.key)
                val headers = headersStore?.get(entry.key)?.headers()?.associate { it.key() to String(it.value(), Charsets.UTF_8) } ?: emptyMap()
                headersStore?.delete(entry.key)
                if (!channel.trySend(SimuleringRequest(entry.key, entry.value.value(), headers)).isSuccess) {
                    backpressure(entry.key, entry.value.value(), headers)
                }
            }
        }
    }

    private fun backpressure(key: String, sim: SimulerBeregningRequest, headers: Map<String, String>) {
        val fagsystem = Fagsystem.from(sim.request.oppdrag.kodeFagomraade.trimEnd())
        backpressureChannel.trySend(SimuleringBackpressure(key, fagsystem, headers))
    }
}

private fun org.apache.kafka.common.header.Headers.toMap(): Map<String, String> =
    associate { it.key() to String(it.value(), Charsets.UTF_8) }
