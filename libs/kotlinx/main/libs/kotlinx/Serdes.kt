package libs.kotlinx

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

val KotlinxJson = Json { 
    ignoreUnknownKeys = true
    encodeDefaults = true
    allowStructuredMapKeys = true
    serializersModule = SerializersModule { 
        contextual(LocalDate::class, LocalDateSerializer)
        contextual(LocalDateTime::class, LocalDateTimeSerializer)
        contextual(UUID::class, UUIDSerializer)
        contextual(Instant::class, InstantSerializer)
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
    override fun deserialize(decoder: Decoder): LocalDate { 
        val jsonDecoder = decoder as? JsonDecoder ?: return LocalDate.parse(decoder.decodeString())
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> {
                val (y, m, d) = element.map { it.jsonPrimitive.int }
                LocalDate.of(y, m, d)
            }
            else -> LocalDate.parse(element.jsonPrimitive.content)
        }
    }
}

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDateTime {
        val jsonDecoder = decoder as? JsonDecoder ?: return parseDateTime(decoder.decodeString())
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> {
                val parts = element.map { it.jsonPrimitive.int }
                LocalDateTime.of(
                    parts[0], 
                    parts[1], 
                    parts[2],
                    parts.getOrElse(3) { 0 },
                    parts.getOrElse(4) { 0 },
                    parts.getOrElse(5) { 0 },
                    parts.getOrElse(6) { 0 },
                )
            }
            else -> parseDateTime(element.jsonPrimitive.content)
        }
    }

    private fun parseDateTime(text: String): LocalDateTime = 
        if (text.endsWith("Z")) LocalDateTime.parse(text.dropLast(1))
        else LocalDateTime.parse(text)
}

object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
