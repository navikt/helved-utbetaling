package fakes

import kotlinx.coroutines.CompletableDeferred
import libs.kafka.Kafka
import libs.kafka.KafkaConfig
import no.nav.utsjekk.kontrakter.iverksett.StatusEndretMelding

class KafkaFake : Kafka<StatusEndretMelding> {
    var produced: CompletableDeferred<StatusEndretMelding> = CompletableDeferred()

    val config = KafkaConfig(
        brokers = "mock",
        truststore = "",
        keystore = "",
        credstorePassword = ""
    )

    override fun produce(key: String, value: StatusEndretMelding) {
        produced.complete(value)
    }

    override fun close() {}

    fun reset() {
        produced = CompletableDeferred()
    }
}
