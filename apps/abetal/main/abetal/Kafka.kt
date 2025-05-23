package abetal

import abetal.models.*
import libs.kafka.*
import libs.kafka.stream.MappedStream
import libs.utils.appLog
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import org.apache.kafka.streams.kstream.Windowed
import org.apache.kafka.streams.processor.PunctuationType
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.ProcessorSupplier
import org.apache.kafka.streams.state.StoreBuilder
import org.apache.kafka.streams.state.TimestampedKeyValueStore
import org.apache.kafka.streams.state.ValueAndTimestamp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

object Topics {
    val aap = Topic("helved.utbetalinger-aap.v1", json<AapUtbetaling>())
    val dp = Topic("helved.utbetalinger-dp.v1", json<DpUtbetaling>())
    val utbetalinger = Topic("helved.utbetalinger.v1", json<Utbetaling>())
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val simulering = Topic("helved.simuleringer.v1", jaxb<SimulerBeregningRequest>())
    val status = Topic("helved.status.v1", json<StatusReply>())
    val saker = Topic("helved.saker.v1", jsonjsonSet<SakKey, UtbetalingId>())
}

object Tables {
    val utbetalinger = Table(Topics.utbetalinger)
    val saker = Table(Topics.saker)
}

object Stores {
    val utbetalinger = Store(Tables.utbetalinger)
}

fun createTopology(): Topology = topology {
    val utbetalinger = consume(Tables.utbetalinger)
    val saker = utbetalingToSak(utbetalinger)
    aapStream(utbetalinger, saker)
    dpStream(utbetalinger, saker)
}

data class UtbetalingLeftJoin(val left: Utbetaling, val right: Utbetaling?)
data class SakKey(val sakId: SakId, val fagsystem: Fagsystem)
data class OppdragAggregate(val utbetalinger: List<Utbetaling>, val oppdrag: Oppdrag)

fun utbetalingToSak(utbetalinger: KTable<String, Utbetaling>): KTable<SakKey, Set<UtbetalingId>> {
    val ktable = utbetalinger
        .toStream()
        .rekey { _, utbetaling -> SakKey(utbetaling.sakId, Fagsystem.from(utbetaling.stønad)) }
        .groupByKey(jsonjson())
        .aggregate(Tables.saker) { _, utbetaling, uids -> 
            when(utbetaling.action) {
                Action.DELETE -> uids - utbetaling.uid
                else -> uids + utbetaling.uid
            }
        } 

    ktable
        .toStream()
        .produce(Topics.saker)

    return ktable
}

fun Topology.aapStream(utbetalinger: KTable<String, Utbetaling>, saker: KTable<SakKey, Set<UtbetalingId>>) {
    consume(Topics.aap)
        .repartition(Topics.aap.serdes, 3)
        .map { key, aap -> AapTuple(key, aap) }
        .rekey { (_, aap) -> SakKey(aap.sakId, Fagsystem.from(aap.stønad)) }
        .leftJoin(jsonjson(), saker)
        .map(::toDomain)
        .rekey { utbetaling -> utbetaling.uid.id.toString() }
        .leftJoin(json(), utbetalinger)
        .branch({ (new, _) -> new.dryrun }, ::dryrunStream)
        .default(::oppdragStream)
}

fun Topology.dpStream(utbetalinger: KTable<String, Utbetaling>, saker: KTable<SakKey, Set<UtbetalingId>>) {
    consume(Topics.dp)
        .repartition(Topics.dp.serdes, 3)
        .map { key, dp -> DpTuple(key, dp) }
        .rekey { (_, dp) -> SakKey(SakId(dp.sakId), Fagsystem.from(dp.stønad)) }
        .leftJoin(jsonjson(), saker)
        .flatMapKeyValue(::splitOnMeldeperiode)
        .leftJoin(json(), utbetalinger)
        .branch({ (new, _) -> new.dryrun }, ::dryrunStream)
        .default(::utbetalingDiffStream)
}

private val suppressProcessorSupplier = SuppressProcessorSupplier(
    100.milliseconds,
    1.seconds,
    "dp-diff-window-agg-session-store",
    windowedjsonList(),
)

fun utbetalingDiffStream(branched: MappedStream<String, StreamsPair<Utbetaling, Utbetaling?>>) {
    branched
        .filter { (new, prev) -> 
            !new.isDuplicate(prev).also { 
                if (it) {
                    appLog.info("Duplicate message found for ${new.uid.id}")
                } else {
                    appLog.info("New : $new")
                    appLog.info("Prev: $prev")
                }
            }
        }
        .rekey { _, (new, _) -> new.originalKey }
        .map { _, streamsPair -> listOf(UtbetalingLeftJoin(streamsPair.left, streamsPair.right)) }
        .sessionWindow(jsonList(), 1.seconds)
        .reduce(suppressProcessorSupplier, "dp-diff-window-agg-session-store") { acc, next -> acc + next }
        .map { aggregate  -> 
            Result.catch { 
                AggregateOppdragService.utled(aggregate)
            }
        }
        .branch({ it.isOk() }) {
            val result = this.map { it -> it.unwrap() }
            result.flatMapKeyAndValue { _, (utbetalinger, _) -> 
                utbetalinger.map { KeyValue(it.uid.id.toString(), it) } 
            }.produce(Topics.utbetalinger)
            result.map { (_, oppdrag) -> oppdrag }.produce(Topics.oppdrag)
            result.map { (_, _) -> StatusReply(Status.MOTTATT) }.produce(Topics.status) // 
        }
        .default {
            this.map { it -> it.unwrapErr() }.produce(Topics.status) 
        }
}

fun oppdragStream(branched: MappedStream<String, StreamsPair<Utbetaling, Utbetaling?>>) {
    branched.filter { (new, prev) ->
        val distinct = !new.isDuplicate(prev)
        if (!distinct) appLog.info("Duplicate message found for ${new.uid.id}")
        distinct
    }.map { (new, prev) ->
        Result.catch {
            new.validate(prev)
            val new = new.copy(perioder = new.perioder.aggreger(new.periodetype))
            val oppdrag = when (new.action) {
                Action.CREATE -> OppdragService.opprett(new)
                Action.UPDATE -> OppdragService.update(new, prev ?: notFound("previous utbetaling"))
                Action.DELETE -> OppdragService.delete(new, prev ?: notFound("previous utbetaling"))
            }
            val lastPeriodeId = PeriodeId.decode(oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId)
            val utbetaling = new.copy(lastPeriodeId = lastPeriodeId)
            utbetaling to oppdrag
        }
    }.branch({ it.isOk() }) {
        val result = this.map { it -> it.unwrap() }
        result.map { (utbetaling, _) -> utbetaling }.produce(Topics.utbetalinger)
        result.map { (_, oppdrag) -> oppdrag }.produce(Topics.oppdrag)
        result.map { (_, _) -> StatusReply(Status.MOTTATT) }.produce(Topics.status)
    }.default {
        map { it -> it.unwrapErr() }.produce(Topics.status)
    }
}

fun dryrunStream(branched: MappedStream<String, StreamsPair<Utbetaling, Utbetaling?>>) {
    branched.map { (new, prev) ->
        Result.catch {
            new.validate(prev)
            val new = new.copy(perioder = new.perioder.aggreger(new.periodetype))
            when (new.action) {
                Action.CREATE -> SimuleringService.opprett(new)
                Action.UPDATE -> SimuleringService.update(new, prev ?: notFound("previous utbetaling"))
                Action.DELETE -> SimuleringService.delete(new, prev ?: notFound("previous utbetaling"))
            }
        }
    }.branch({ it.isOk() }) {
        val result = this.map { it -> it.unwrap() }
        result.map { _ -> StatusReply(Status.MOTTATT) }.produce(Topics.status)
        result.produce(Topics.simulering)
    }.default {
        map { it -> it.unwrapErr() }.produce(Topics.status)
    }
}

class SuppressProcessorSupplier(
    private val punctuationInterval: Duration,
    private val inactivityGap: Duration,
    private val stateStoreName: StateStoreName,
    private val serdes: Serdes<Windowed<String>, List<UtbetalingLeftJoin>>,
) : ProcessorSupplier<Windowed<String>, List<UtbetalingLeftJoin>, String, List<UtbetalingLeftJoin>> {

    override fun stores(): Set<StoreBuilder<*>> {
        return setOf(org.apache.kafka.streams.state.Stores.timestampedKeyValueStoreBuilder(
            org.apache.kafka.streams.state.Stores.persistentTimestampedKeyValueStore(stateStoreName),
            serdes.key,
            serdes.value
        ))
    }

    override fun get(): Processor<Windowed<String>, List<UtbetalingLeftJoin>, String, List<UtbetalingLeftJoin>> {
        return SuppressProcessor(punctuationInterval, inactivityGap, stateStoreName)
    }
}

class SuppressProcessor(
    private val punctuationInterval: Duration,
    private val inactivityGap: Duration,
    private val stateStoreName: StateStoreName,
): Processor<Windowed<String>, List<UtbetalingLeftJoin>, String, List<UtbetalingLeftJoin>> {
    private lateinit var bufferStore: TimestampedKeyValueStore<Windowed<String>, List<UtbetalingLeftJoin>>
    private lateinit var context: ProcessorContext<String, List<UtbetalingLeftJoin>>

    override fun init(ctx: ProcessorContext<String, List<UtbetalingLeftJoin>>) {
        context = ctx
        bufferStore = context.getStateStore(stateStoreName) as TimestampedKeyValueStore<Windowed<String>, List<UtbetalingLeftJoin>>
        context.schedule(punctuationInterval.toJavaDuration(), PunctuationType.WALL_CLOCK_TIME, ::punctuate)
    } 

    override fun process(record: org.apache.kafka.streams.processor.api.Record<Windowed<String>, List<UtbetalingLeftJoin>>) {
        bufferStore.put(record.key(), ValueAndTimestamp.make(record.value(), record.timestamp()))
    }

    private fun punctuate(wallClockTime: Long) {
        val iterator = bufferStore.all()
        val windowsToEmit = mutableListOf<Windowed<String>>()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val windowedKey = entry.key
            val valueAndTimestamp = entry.value
            val windowEndTime = windowedKey.window().endTime().toEpochMilli()
            val emissionTimeThreshold = windowEndTime + inactivityGap.inWholeMilliseconds

            if (wallClockTime > emissionTimeThreshold) {
                val latestAggregate = valueAndTimestamp.value() 
                val lastestRecordTimestamp = valueAndTimestamp.timestamp()
                context.forward(org.apache.kafka.streams.processor.api.Record(windowedKey.key(), latestAggregate, lastestRecordTimestamp))
                windowsToEmit.add(windowedKey)
            }
        }
        iterator.close()
        windowsToEmit.forEach(bufferStore::delete)
    }
}

