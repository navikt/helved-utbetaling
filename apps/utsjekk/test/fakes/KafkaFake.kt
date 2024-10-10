package fakes

import kotlinx.coroutines.CompletableDeferred
import libs.kafka.Kafka
import libs.kafka.KafkaConfig
import no.nav.utsjekk.kontrakter.iverksett.StatusEndretMelding

class KafkaFake : Kafka<StatusEndretMelding> {
    private val produced = mutableMapOf<String, CompletableDeferred<StatusEndretMelding>>()
    val config = KafkaConfig(
        brokers = "mock",
        truststore = "",
        keystore = "",
        credstorePassword = ""
    )

    fun expect(key: String) {
        produced[key] = CompletableDeferred()
    }

    fun reset() = produced.clear()

    suspend fun waitFor(key: String): StatusEndretMelding =
        produced[key]?.await() ?: error("kafka fake is not setup to expect $key")

    override fun produce(key: String, value: StatusEndretMelding) {
        produced[key]?.complete(value)
    }

    override fun close() {}
}
