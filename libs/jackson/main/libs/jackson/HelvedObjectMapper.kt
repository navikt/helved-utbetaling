package libs.jackson

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import models.ApiError
import models.Info
import models.Simulering
import models.Stønadstype
import models.v1
import models.v2

val objectMapper: ObjectMapper = jacksonObjectMapper().registerHelvedModules()

fun ObjectMapper.registerHelvedModules(): ObjectMapper =
    apply {
        registerModule(JavaTimeModule())
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        addMixIn(Simulering::class.java, SimuleringMixin::class.java)
        addMixIn(ApiError::class.java, ApiErrorMixin::class.java)
        registerModule(
            SimpleModule().apply {
                addDeserializer(Stønadstype::class.java, StønadstypeDeserializer())
            }
        )
    }

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes(
    JsonSubTypes.Type(value = v1.Simulering::class, name = "v1"),
    JsonSubTypes.Type(value = v2.Simulering::class, name = "v2"),
    JsonSubTypes.Type(value = Info::class, name = "info"),
)
private interface SimuleringMixin

@JsonIgnoreProperties(
    value = ["cause", "localizedMessage", "message", "stackTrace", "suppressed"],
)
private interface ApiErrorMixin

private class StønadstypeDeserializer : JsonDeserializer<Stønadstype>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Stønadstype {
        return Stønadstype.valueOf(parser.valueAsString)
    }
}
