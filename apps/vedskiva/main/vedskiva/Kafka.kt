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
import kotlin.time.Duration.Companion.minutes
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
    kafka: Kafka,
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

        if (today == last?.created_at) return // already done

        oppdragsdataConsumer.seekToBeginning(0, 1, 2)
        val records = oppdragsdataConsumer.poll(1.minutes)
        if (records.isEmpty()) return // TODO: skal vi avstemme selv om det ikke er noe å avstemme?

        val avstemFom = last?.avstemt_tom?.plusDays(1) ?: LocalDate.now().forrigeVirkedag() 
        val avstemTom = today.minusDays(1)

        records
            .filter { record -> record.value != null } 
            .map { record -> record as Record<String, Oppdragsdata> }
            .filter { record  -> record.value.avstemmingsdag == today || record.value.avstemmingsdag.isBefore(today) }
            .groupBy { record -> record.value.fagsystem }
            .forEach { (fagsystem, oppdragsdatas) ->
                val avstemming = Avstemming(avstemFom, avstemTom, oppdragsdatas.map { record -> record.value })
                val messages = AvstemmingService.create(avstemming)
                val avstemmingId = messages.first().aksjon.avleverendeAvstemmingId
                messages.forEach { message ->
                    avstemmingProducer.send(avstemmingId, message, 0)
                }
                appLog.info("Fullført grensesnittavstemming for ${fagsystem.name} id: $avstemmingId")
                oppdragsdatas.forEach { record ->
                    oppdragsdataProducer.send(record.key, null, record.partition)
                }
            } // TODO: merge med tom liste av avstemminger (START/SLUTT melding)

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

