package oppdrag.postgres

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

internal val jackson: ObjectMapper = jacksonObjectMapper().apply {
    registerModule(JavaTimeModule())
    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}
