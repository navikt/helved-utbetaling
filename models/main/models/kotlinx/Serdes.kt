package models.kotlinx

import kotlinx.serialization.json.Json
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

// TODO: move this to kafka/serde or a common serde lib?
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
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDateTime {
        val text = decoder.decodeString()
        return if (text.endsWith("Z")) LocalDateTime.parse(text.dropLast(1))
        else LocalDateTime.parse(text)
    }
}

object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
