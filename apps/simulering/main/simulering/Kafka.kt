package simulering

import kotlinx.coroutines.channels.Channel
import libs.kafka.*
import libs.kafka.processor.StateScheduleProcessor
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
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
}

fun Topology.simuleringer(channel: Channel<Pair<String, SimulerBeregningRequest>>) {
    globalKTable(Tables.dryrunAap, retention = 1.hours)
    globalKTable(Tables.dryrunDp, retention = 1.hours)
    globalKTable(Tables.dryrunTp, retention = 1.hours)
    globalKTable(Tables.dryrunTs, retention = 1.hours)

    val ktable = consume(Tables.simuleringer)
    val scheduler = SimuleringScheduler(ktable, 5.seconds, channel)
    ktable.schedule(scheduler)
}

class SimuleringScheduler(
    ktable: KTable<String, SimulerBeregningRequest>,
    interval: Duration,
    private val channel: Channel<Pair<String, SimulerBeregningRequest>>,
    private val evictionTtl: Duration = 3.minutes, // TODO: Finn riktig verdi her
) : StateScheduleProcessor<String, SimulerBeregningRequest>(
    named = "simulering-scheduler",
    table = ktable,
    interval = interval,
) {
    override fun schedule(wallClockTime: Long, store: StateStore<String, ValueAndTimestamp<SimulerBeregningRequest>>) {
        val iter = store.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val age = wallClockTime - entry.value.timestamp()
            if (age > evictionTtl.inWholeMilliseconds) {
                store.delete(entry.key)
            } else if (channel.trySend(entry.key to entry.value.value()).isSuccess) {
                store.delete(entry.key)
            }
        }
    }
}

