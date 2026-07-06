package simulering

import kotlinx.coroutines.channels.Channel
import libs.kafka.*
import libs.kafka.processor.StateScheduleProcessor
import models.Simulering
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import org.apache.kafka.streams.state.ValueAndTimestamp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object Topics {
    val simuleringer = Topic("helved.simuleringer.v1", jaxb<SimulerBeregningRequest>())
    val dryrunAap = Topic("helved.dryrun-aap.v1", json<Simulering>())
    val dryrunDp = Topic("helved.dryrun-dp.v1", json<Simulering>())
    val dryrunTs = Topic("helved.dryrun-ts.v1", json<Simulering>())
    val dryrunTp = Topic("helved.dryrun-tp.v1", json<Simulering>())
}

object Tables {
    val simuleringer = Table(Topics.simuleringer)
}

fun Topology.simuleringer(channel: Channel<Pair<String, SimulerBeregningRequest>>) {
    val ktable = consume(Tables.simuleringer)
    val scheduler = SimuleringScheduler(ktable, 5.seconds, channel)
    ktable.schedule(scheduler)
}

class SimuleringScheduler(
    ktable: KTable<String, SimulerBeregningRequest>,
    interval: Duration,
    private val channel: Channel<Pair<String, SimulerBeregningRequest>>,
    private val evictionTtl: Duration = 2.minutes,
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
            if (age <= evictionTtl.inWholeMilliseconds) {
                channel.trySend(entry.key to entry.value.value())
            }
        }
    }
}

