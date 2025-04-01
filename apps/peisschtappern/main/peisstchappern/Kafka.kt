package peisstchappern

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.kafka.*
import libs.postgres.Jdbc
import libs.postgres.concurrency.transaction

object Topics {
    val oppdrag = Topic("helved.oppdrag.v1", bytes())
    val kvittering = Topic("helved.kvittering.v1", bytes())
}

fun createTopology(): Topology = topology {
    save(Topics.oppdrag, Tables.oppdrag)
    save(Topics.kvittering, Tables.kvittering)
}

private fun Topology.save(topic: Topic<String, ByteArray>, table: Tables) {
    consume(topic) { key, value, metadata ->
        runBlocking {
            withContext(Jdbc.context) {
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
                    ).insert(table)
                }
            }
        }
    }
}

