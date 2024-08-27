package utsjekk.iverksetting

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.KeyDeserializer
import com.fasterxml.jackson.databind.SerializerProvider
import no.nav.utsjekk.kontrakter.felles.objectMapper

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes(
    JsonSubTypes.Type(KjedenøkkelMeldeplikt::class, name = "meldeplikt"),
    JsonSubTypes.Type(KjedenøkkelStandard::class, name = "standard"),
)
sealed class Kjedenøkkel(
    open val klassifiseringskode: String,
)

data class KjedenøkkelMeldeplikt(
    override val klassifiseringskode: String,
    val meldekortId: String,
) : Kjedenøkkel(klassifiseringskode)

data class KjedenøkkelStandard(
    override val klassifiseringskode: String,
) : Kjedenøkkel(klassifiseringskode)

class KjedenøkkelKeySerializer : JsonSerializer<Kjedenøkkel>() {
    override fun serialize(
        value: Kjedenøkkel?,
        gen: JsonGenerator?,
        serializers: SerializerProvider?,
    ) {
        gen?.let { jGen ->
            value?.let { kjedenøkkel ->
                jGen.writeFieldName(objectMapper.writeValueAsString(kjedenøkkel))
            } ?: jGen.writeNull()
        }
    }
}

class KjedenøkkelKeyDeserializer : KeyDeserializer() {
    override fun deserializeKey(
        key: String?,
        ctx: DeserializationContext?,
    ): Kjedenøkkel? = key?.let { objectMapper.readValue(key, Kjedenøkkel::class.java) }
}
