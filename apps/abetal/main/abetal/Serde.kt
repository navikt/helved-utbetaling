package abetal

import libs.kafka.JacksonDeserializer
import kotlin.reflect.KClass


class DeserialiseringFeiletException(msg: String) : Exception(msg)

internal fun <T : Any> deserialize(
    topic: String,
    payload: ByteArray,
    target: KClass<T>,
): T {
    return try {
        checkNotNull(JacksonDeserializer(target).deserialize(topic, payload)) {
            "Mottok tom payload på $topic"
        }
    } catch (_: Exception) {
        throw DeserialiseringFeiletException("Feil ved deserialisering av melding fra $topic")
    }
}