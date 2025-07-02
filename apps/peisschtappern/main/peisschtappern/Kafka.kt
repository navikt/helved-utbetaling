package peisschtappern

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.kafka.*
import libs.tracing.*
import libs.jdbc.Jdbc
import libs.jdbc.concurrency.transaction
import no.trygdeetaten.skjema.oppdrag.Oppdrag


object Topics {
    val avstemming = Topic("helved.avstemming.v1", bytes())
    val oppdrag = Topic("helved.oppdrag.v1", bytes())
    val kvittering = Topic("helved.kvittering.v1", bytes())
    val simuleringer = Topic("helved.simuleringer.v1", bytes())
    val utbetalinger = Topic("helved.utbetalinger.v1", bytes())
    val saker = Topic("helved.saker.v1", bytes())
    val aap = Topic("helved.utbetalinger-aap.v1", bytes())
    val dryrunAap = Topic("helved.dryrun-aap.v1", bytes())
    val dryrunTp = Topic("helved.dryrun-tp.v1", bytes())
    val dryrunTs = Topic("helved.dryrun-ts.v1", bytes())
    val dryrunDp = Topic("helved.dryrun-dp.v1", bytes())
    val status = Topic("helved.status.v1", bytes())
    val pendingUtbetalinger = Topic("helved.pending-utbetalinger.v1", bytes())
    val fk = Topic("helved.fk.v1", bytes())
}

val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())

fun createTopology(): Topology = topology {
    Channel.all()
        .sortedBy { it.revision }
        .forEach { save(it.topic, it.table) }
}

private fun Topology.save(topic: Topic<String, ByteArray>, table: Table) {
    forEach(topic) { key, value, metadata ->
        runBlocking {
            withContext(Jdbc.context + Dispatchers.IO) {
                transaction {
                    Dao(
                        version = topic.name.substringAfterLast("."),
                        topic_name = topic.name,
                        key = key,
                        value = value?.decodeToString(),
                        partition = metadata.partition,
                        offset = metadata.offset,
                        timestamp_ms = metadata.timestamp,
                        stream_time_ms = metadata.streamTimeMs,
                        system_time_ms = metadata.systemTimeMs,
                        trace_id = Tracing.getCurrentTraceId(),
                    ).insert(table)
                }
            }
        }
    }
}

class DefaultKafkaFactory : KafkaFactory {}

