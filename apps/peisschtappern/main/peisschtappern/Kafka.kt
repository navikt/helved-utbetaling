package peisschtappern

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.jdbc.Jdbc
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import libs.jdbc.concurrency.transaction
import libs.kafka.*
import libs.xml.XMLMapper
import libs.kafka.processor.*
import libs.tracing.Tracing

object Topics {
    val avstemming = Topic("helved.avstemming.v1", bytes())
    val oppdrag = Topic("helved.oppdrag.v1", bytes())
    val kvittering = Topic("helved.kvittering.v1", bytes())
    val simuleringer = Topic("helved.simuleringer.v1", bytes())
    val utbetalinger = Topic("helved.utbetalinger.v1", bytes())
    val saker = Topic("helved.saker.v1", bytes())
    val aap = Topic("aap.utbetaling.v1", bytes())
    val dp = Topic("teamdagpenger.utbetaling.v1", bytes())
    val dpIntern = Topic("helved.utbetalinger-dp.v1", bytes())
    val dryrunAap = Topic("helved.dryrun-aap.v1", bytes())
    val dryrunTp = Topic("helved.dryrun-tp.v1", bytes())
    val dryrunTs = Topic("helved.dryrun-ts.v1", bytes())
    val dryrunDp = Topic("helved.dryrun-dp.v1", bytes())
    val status = Topic("helved.status.v1", bytes())
    val pendingUtbetalinger = Topic("helved.pending-utbetalinger.v1", bytes())
    val fk = Topic("helved.fk.v1", bytes())
    val tsIntern = Topic("helved.utbetalinger-ts.v1", bytes())
    val tpIntern = Topic("helved.utbetalinger-tp.v1", bytes())
    val ts = Topic("tilleggsstonader.utbetaling.v1", bytes())
    val aapIntern = Topic("helved.utbetalinger-aap.v1", bytes())
    val historisk = Topic("historisk.utbetaling.v1", bytes())
    val historiskIntern = Topic("helved.utbetalinger-historisk.v1", bytes())
}

fun createTopology(config: Config): Topology = topology {
    Channel.all()
        .sortedBy { it.revision }
        .forEach { save(it.topic, it.table, config.image.commitHash()) }
}

data class AuditMetadata (
    val timestamp: Long,
    val streamTimeMs: Long,
    val systemTimeMs: Long,
) {
    companion object {
        fun parse(metadata: Metadata): AuditMetadata {
            val ts = metadata.headers[AUD_TIMESTAMP_MS]?.toLongOrNull() ?: metadata.timestamp
            val st = metadata.headers[AUD_STREAM_TIME_MS]?.toLongOrNull() ?: metadata.streamTimeMs
            val sy = metadata.headers[AUD_SYSTEM_TIME_MS]?.toLongOrNull() ?: metadata.systemTimeMs

            return AuditMetadata(ts, st, sy)
        }
    }
}

private fun Topology.save(
    topic: Topic<String, ByteArray>,
    table: Table,
    commitHash: String,
) {
    consume(topic, includeTombstones = true)
        .processor(Processor{ EnrichMetadataProcessor() })
        .forEach { key, (value, metadata) ->
            runBlocking {
                withContext(Jdbc.context + Dispatchers.IO) {
                    transaction {
                        if (topic == Topics.oppdrag) {
                            AlertService.missingKvitteringHandler(key, value)
                        }
                        val aud = AuditMetadata.parse(metadata) 
                        Daos(
                            version = topic.name.substringAfterLast("."),
                            topic_name = topic.name,
                            key = key,
                            value = value?.decodeToString(),
                            partition = metadata.partition,
                            offset = metadata.offset,
                            timestamp_ms = aud.timestamp,
                            stream_time_ms = aud.streamTimeMs,
                            system_time_ms = aud.systemTimeMs,
                            trace_id = Tracing.getCurrentTraceId(),
                            commit = commitHash, 
                        ).insert(table)
                    }
                }
            }
        }
}

class DefaultKafkaFactory : KafkaFactory {}

private fun String.commitHash(): String = substringAfterLast(":")
