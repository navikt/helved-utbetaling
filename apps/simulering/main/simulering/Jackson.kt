package simulering

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import models.kontrakter.Personident
import org.http4k.format.ConfigurableKotlinxSerialization
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

object PersonidentSerializer : KSerializer<Personident> {
    override val descriptor = PrimitiveSerialDescriptor("Personident", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Personident) = encoder.encodeString(value.verdi)
    override fun deserialize(decoder: Decoder): Personident {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            val verdi = when (element) {
                is JsonPrimitive -> element.content
                is JsonObject -> element["verdi"]!!.jsonPrimitive.content
                else -> error("Unexpected JSON for Personident: $element")
            }
            return Personident(verdi)
        }
        return Personident(decoder.decodeString())
    }
}

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())
}

val jsonModule = SerializersModule {
    contextual(PersonidentSerializer)
    contextual(UUIDSerializer)
    contextual(LocalDateSerializer)
    contextual(LocalDateTimeSerializer)
}

object KotlinxJson : ConfigurableKotlinxSerialization({
    ignoreUnknownKeys = true
    serializersModule = jsonModule
})
