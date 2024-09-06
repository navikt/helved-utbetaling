package fakes

import libs.kafka.KafkaConfig
import no.nav.utsjekk.kontrakter.iverksett.StatusEndretMelding
import utsjekk.status.Kafka

class KafkaFake: Kafka<StatusEndretMelding> {
    private val produced = mutableMapOf<String, StatusEndretMelding>()

    val config = KafkaConfig(
        brokers = "mock",
        truststore = "",
        keystore = "",
        credstorePassword = ""
    )

    fun hasProduced(key: String): Boolean = produced.containsKey(key)

    override fun produce(key: String, value: StatusEndretMelding) {
        produced[key] = value
    }

    override fun close() {
        produced.clear()
    }
}
