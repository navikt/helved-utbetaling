package vedskiva

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import libs.postgres.concurrency.transaction
import libs.kafka.*
import libs.postgres.Jdbc
import libs.utils.secureLog
import models.Avstemming
import models.Oppdragsdata
import models.forrigeVirkedag
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import kotlin.time.Duration.Companion.seconds
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition

object Topics {
    val avstemming = Topic("helved.avstemming.v1", xml<Avstemmingsdata>())
    val oppdragsdata = Topic("helved.oppdragsdata.v1", json<Oppdragsdata>())
}

open class Kafka : KafkaFactory

class OppdragsdataConsumer(
    config: StreamsConfig,
    kafka: KafkaFactory,
): AutoCloseable {
    private val oppdragsdataConsumer = kafka.createConsumer(config, Topics.oppdragsdata, maxProcessingTimeMs = 30_000)
    private val oppdragsdataProducer = kafka.createProducer(config, Topics.oppdragsdata)
    private val avstemmingProducer = kafka.createProducer(config, Topics.avstemming)

    @Suppress("UNCHECKED_CAST")
    suspend fun consumeFromBeginning() {
        val today = LocalDate.now()

        val last: Scheduled? = transaction {
            Scheduled.lastOrNull()
        } 

        if (today == last?.created_at) {
            appLog.info("Already avstemt today")
            return // already done
        }

        oppdragsdataConsumer.seekToBeginning(0, 1, 2)

        val records = mutableMapOf<String, Set<Record<String, Oppdragsdata>>>()
        var keepPolling = true

        while (keepPolling) {
            val polledRecords = oppdragsdataConsumer.poll(1.seconds)

            if (polledRecords.isEmpty()) {
                keepPolling = false
                continue
            }

            polledRecords
                .filter { record ->  
                    if (record.value == null) {
                        true // ta med tombstones slik at vi kan rydde i mutableMapOf<String, Record<String, Oppdragsdata>>()
                    } else {
                        val oppdragsdata = requireNotNull(record.value) { "tombstones skal allerede ha blitt vurdert på dette stedet" }
                        oppdragsdata.avstemmingsdag == today || oppdragsdata.avstemmingsdag.isBefore(today)
                    }
                }
                .forEach { record -> 
                    if (record.value == null) {
                        records.remove(record.key)
                    } else {
                        val record = record as Record<String, Oppdragsdata>
                        records.removeUkvittert(record)
                        records.accumulateAndDeduplicate(record)
                    }
                }

            keepPolling = polledRecords.any { record ->
                val avstemmingsdag = requireNotNull(record.value).avstemmingsdag
                avstemmingsdag == today || avstemmingsdag.isBefore(today) 
            }
        }

        if (records.isEmpty()) {
            appLog.info("No records was found for avstemming.")
            return
        }

        val avstemFom = last?.avstemt_tom?.plusDays(1) ?: LocalDate.now().forrigeVirkedag() 
        val avstemTom = today.minusDays(1)

        records.values
            .filterNot { it.isEmpty() }
            .groupBy { record -> record.first().value.fagsystem }
            .forEach { (fagsystem, oppdragsdatas) ->
                val avstemming = Avstemming(avstemFom, avstemTom, oppdragsdatas.flatMap { records -> records.map{ record -> record.value }})
                val messages = AvstemmingService.create(avstemming)
                val avstemmingId = messages.first().aksjon.avleverendeAvstemmingId
                messages.forEach { message -> avstemmingProducer.send(avstemmingId, message, 0) }
                oppdragsdatas.forEach { record ->
                    // all records within this loop has the same key and partition
                    oppdragsdataProducer.send(record.first().key, null, record.first().partition)
                }
                appLog.info("Fullført grensesnittavstemming for ${fagsystem.name} id: $avstemmingId")
            }

        transaction {
            Scheduled(LocalDate.now(), avstemFom, avstemTom).insert()
        }
    }

    override fun close() {
        oppdragsdataConsumer.close()
        oppdragsdataProducer.close()
        avstemmingProducer.close()
    }
}

// add updated oppdragsdata if not identical to previous
fun MutableMap<String, Set<Record<String, Oppdragsdata>>>.accumulateAndDeduplicate(record: Record<String, Oppdragsdata>) {
    val recordsForKey = getOrDefault(record.key, emptySet())
    this[record.key] = recordsForKey + record
    appLog.info("accumulate ${record.key}: ${this[record.key]!!.size}")
}

// replace record that is updated with a kvittering
fun MutableMap<String, Set<Record<String, Oppdragsdata>>>.removeUkvittert(record: Record<String, Oppdragsdata>) {
    val recordsForKey = getOrDefault(record.key, emptySet())

    val associatedRecordWithoutKvittering = recordsForKey.find { r ->
        r.value.lastDelytelseId == record.value.lastDelytelseId && r.value.kvittering == null
    }

    if (associatedRecordWithoutKvittering != null) {
        this[record.key] = recordsForKey - associatedRecordWithoutKvittering 
        appLog.info("replace ${record.key} with kvittert: ${this[record.key]!!.size}")
    } 

}

