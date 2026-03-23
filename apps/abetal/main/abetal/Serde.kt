package abetal

import libs.kafka.JacksonDeserializer
import kotlin.reflect.KClass


internal fun <T : Any> deserialize(
    topic: String,
    payload: ByteArray,
    target: KClass<T>,
): T {
    return try {
        checkNotNull(JacksonDeserializer(target).deserialize(topic, payload)) {
            "Mottok tom payload på $topic"
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("Feil ved deserialisering av melding fra $topic", e)
    }
}