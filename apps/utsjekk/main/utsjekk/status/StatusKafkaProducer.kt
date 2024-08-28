package utsjekk.status

import libs.kafka.KafkaConfig
import libs.kafka.KafkaFactory
import libs.utils.appLog
import libs.utils.secureLog
import no.nav.utsjekk.kontrakter.felles.objectMapper
import no.nav.utsjekk.kontrakter.iverksett.StatusEndretMelding
import org.apache.kafka.clients.producer.ProducerRecord

// todo: bytt ut med den fra libs.kafka
interface Kafka<T> : AutoCloseable {
    fun produce(key: String, value: T)
}

class StatusKafkaProducer(config: KafkaConfig) : Kafka<StatusEndretMelding> {
    private val producer = KafkaFactory.createProducer("i-status", config)
    private val topic = "helved.iverksetting-status-v1"

    override fun produce(key: String, value: StatusEndretMelding) {
        val json = objectMapper.writeValueAsString(value)
        val record = ProducerRecord(topic, key, json)

        producer.send(record) { metadata, err ->
            if (err != null) {
                appLog.error("Klarte ikke sende status til $topic ($metadata)")
                secureLog.error("Klarte ikke sende status til topic $topic", err)
            } else {
                secureLog.debug("Status produsert for {} til {} ({})", key, topic, metadata)
            }
        }.get() // run blocking
    }

    override fun close() {
        producer.close()
    }
}