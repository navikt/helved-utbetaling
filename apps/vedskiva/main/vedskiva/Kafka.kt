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
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition

object Topics {
    val avstemming = Topic("helved.avstemming.v1", xml<Avstemmingsdata>())
    val oppdragsdata = Topic("helved.oppdragsdata.v1", json<Oppdragsdata>())
}

open class Kafka : ConsumerFactory, ProducerFactory

class OppdragsdataConsumer(
    config: StreamsConfig,
    kafka: Kafka,
): AutoCloseable {
    private val consumer = kafka.createConsumer(config, Topics.oppdragsdata, 30_000)
    private val producer = kafka.createProducer(config, Topics.oppdragsdata)
    private val avstemmingProducer = kafka.createProducer(config, Topics.avstemming)

    suspend fun consumeFromBeginning() {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()

        val last: Scheduled? = transaction {
            Scheduled.lastOrNull()
        } 

        if (today == last?.created_at) return // already done

        val partitions = listOf(
            TopicPartition(Topics.oppdragsdata.name, 0),
            TopicPartition(Topics.oppdragsdata.name, 1),
            TopicPartition(Topics.oppdragsdata.name, 2),
        )
        consumer.assign(partitions)
        consumer.seekToBeginning(partitions)
        val records = consumer.poll(Duration.ofMinutes(1))
        if (records.isEmpty) return

        val avstemFom = last?.avstemt_tom?.plusDays(1) ?: LocalDate.now().forrigeVirkedag() 
        val avstemTom = today.minusDays(1)

        records
            .filter { it.value().avstemmingsdag == today || it.value().avstemmingsdag.isBefore(today) }
            .groupBy { it.value().fagsystem }
            .forEach { (fagsystem, oppdragsdatas) ->
                val avstemming = Avstemming(avstemFom, avstemTom, oppdragsdatas.map { it.value() })
                val messages = AvstemmingService.create(avstemming)
                val avstemmingId = messages.first().aksjon.avleverendeAvstemmingId
                messages.forEach { message ->
                    avstemmingProducer.send(Topics.avstemming, avstemmingId, message)
                }
                appLog.info("Fullført grensesnittavstemming for ${fagsystem.name} id: $avstemmingId")
                oppdragsdatas.forEach {
                    producer.send(Topics.oppdragsdata, it.key(), null, it.partition())
                }
            }

        transaction {
            Scheduled(LocalDate.now(), avstemFom, avstemTom).insert()
        }
    }

    override fun close() {
        consumer.close()
        producer.close()
        avstemmingProducer.close()
    }
}

fun <K, V> Producer<K, V>.send(topic: Topic<K & Any, V & Any>, key: K, value: V?, partition: Int = 0) {
    val record = ProducerRecord<K, V>(Topics.oppdragsdata.name, partition, key, value)
    this.send(record) { metadata, err ->
        if (err != null) {
            appLog.error("Failed to produce record on ${topic.name} ($metadata)")
            secureLog.error("Failed to produce record on ${topic.name} ($metadata)", err)
        } else {
            secureLog.trace("Produce record on ${topic.name} $key = $value ($metadata)")
        }
    }.get()
}

