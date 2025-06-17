package abetal

import abetal.models.*
import libs.kafka.*
import libs.kafka.processor.SuppressProcessor
import libs.kafka.stream.MappedStream
import libs.utils.appLog
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object Topics {
    val dp = Topic("teamdagpenger.utbetaling.v1", json<DpUtbetaling>())
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
    dpStream(utbetalinger, saker)
}

data class SakKey(val sakId: SakId, val fagsystem: Fagsystem)

/**
 * Hver gang helved.utbetalinger.v1 blir produsert til
 * akkumulerer vi uids (UtbetalingID) for saken og erstatter aggregatet på helved.saker.v1
 */
fun utbetalingToSak(utbetalinger: KTable<String, Utbetaling>): KTable<SakKey, Set<UtbetalingId>> {
    val ktable = utbetalinger
        .toStream()
        .rekey { _, utbetaling -> SakKey(utbetaling.sakId, Fagsystem.from(utbetaling.stønad)) }
        .groupByKey(Serde.json(), Serde.json())
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

fun Topology.dpStream(utbetalinger: KTable<String, Utbetaling>, saker: KTable<SakKey, Set<UtbetalingId>>) {
    consume(Topics.dp)
        .repartition(Topics.dp.serdes, 3)
        .map { key, dp -> DpTuple(key, dp) }
        .rekey { (_, dp) -> SakKey(SakId(dp.sakId), Fagsystem.DAGPENGER) }
        .leftJoin(Serde.json(), Serde.json(), saker)
        .flatMapKeyValue(::splitOnMeldeperiode)
        .leftJoin(Serde.string(), Serde.json(), utbetalinger)
        .filter { (new, prev) -> !new.isDuplicate(prev) }
        .rekey { new, _ -> new.originalKey }
        .map { new, prev -> listOf(StreamsPair(new, prev)) }
        .sessionWindow(Serde.string(), Serde.listStreamsPair(), 1.seconds)
        .reduce(suppressProcessorSupplier, "dp-diff-window-agg-session-store") { acc, next -> acc + next }
        .map { aggregate  -> 
            Result.catch { 
                val oppdragAgg = AggregateService.utledOppdrag(aggregate.filter { (new, _) -> !new.dryrun })
                val dryrunAgg = AggregateService.utledSimulering(aggregate.filter { (new, _) -> new.dryrun })
                oppdragAgg to dryrunAgg
            }
        }
        .branch({ it.isOk() }) {
            val result = this.map { it -> it.unwrap() }

            result
                .flatMapKeyAndValue { _, (oppdragAgg, _) -> oppdragAgg.utbets.map { KeyValue(it.uid.toString(), it) }}
                .produce(Topics.utbetalinger)

            result
                .flatMap { (_, simAgg) -> simAgg.sims } 
                .produce(Topics.simulering)

            val oppdrag = result.flatMap { (oppdragAgg, _) -> oppdragAgg.opps }
            oppdrag.produce(Topics.oppdrag)
            oppdrag.map { StatusReply.mottatt(it) }.produce(Topics.status)
        }
        .default {
            this.map { it -> it.unwrapErr() }.produce(Topics.status) 
        }
}

private val suppressProcessorSupplier = SuppressProcessor.supplier(
    keySerde = WindowedStringSerde,
    valueSerde = JsonSerde.listStreamsPair<Utbetaling, Utbetaling?>(), 
    punctuationInterval = 100.milliseconds,
    inactivityGap = 1.seconds,
    stateStoreName = "dp-diff-window-agg-session-store",
)


