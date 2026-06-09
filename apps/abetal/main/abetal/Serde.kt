package abetal

import libs.kafka.KotlinxDeserializer
import kotlinx.serialization.serializer


class DeserialiseringFeiletException(msg: String) : Exception(msg)

internal inline fun <reified T : Any> deserialize(
    topic: String,
    payload: ByteArray,
): T {
    return try {
        checkNotNull(KotlinxDeserializer(serializer<T>()).deserialize(topic, payload)) {
            "Mottok tom payload på $topic"
        }
    } catch (_: Exception) {
        throw DeserialiseringFeiletException("Feil ved deserialisering av melding fra $topic")
    }
}
