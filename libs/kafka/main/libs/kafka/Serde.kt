package libs.kafka

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.reflect.KClass
import libs.xml.*
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer

data class Serdes<K: Any, V>(
    val key: StreamSerde<K>, 
    val value: StreamSerde<V>,
)

interface StreamSerde<T> : Serde<T>

fun string() = Serdes(StringSerde, StringSerde)
inline fun <reified V: Any> json() = Serdes(StringSerde, JsonSerde.jackson<V>())
inline fun <reified V: Any> xml() = Serdes(StringSerde, XmlSerde.xml<V>())
inline fun <reified V: Any> jaxb() = Serdes(StringSerde, XmlSerde.jaxb<V>())

object StringSerde : StreamSerde<String> {
    private val internalSerde = Serdes.StringSerde()
    override fun serializer(): Serializer<String> = internalSerde.serializer()
    override fun deserializer(): Deserializer<String> = internalSerde.deserializer()
}

object ByteArraySerde: StreamSerde<ByteArray> {
    private val internalSerde = org.apache.kafka.common.serialization.Serdes.ByteArraySerde()
    override fun serializer(): Serializer<ByteArray> = internalSerde.serializer()
    override fun deserializer(): Deserializer<ByteArray> = internalSerde.deserializer()
}

object JsonSerde {
    inline fun <reified V : Any> jackson(): StreamSerde<V> = object : StreamSerde<V> {
        override fun serializer(): Serializer<V> = JacksonSerializer()
        override fun deserializer(): Deserializer<V> = JacksonDeserializer(V::class)
    }

internal val jackson: ObjectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}

class JacksonSerializer<T : Any> : Serializer<T> {
    override fun serialize(topic: String, data: T?): ByteArray? {
        return data?.let {
            JsonSerde.jackson.writeValueAsBytes(data)
        }
    }
}

class JacksonDeserializer<T : Any>(private val kclass: KClass<T>) : Deserializer<T> {
    override fun deserialize(topic: String, data: ByteArray?): T? {
        return data?.let {
            JsonSerde.jackson.readValue(data, kclass.java)
        }
    }
}

object XmlSerde {
    inline fun <reified V : Any> xml(): StreamSerde<V> = object : StreamSerde<V> {
        private val mapper: XMLMapper<V> = XMLMapper()
        override fun serializer(): Serializer<V> = XmlSerializer(mapper)
        override fun deserializer(): Deserializer<V> = XmlDeserializer(mapper)
    }

    inline fun <reified V : Any> jaxb(): StreamSerde<V> = object : StreamSerde<V> {
        private val mapper: XMLMapper<V> = XMLMapper(false)
        override fun serializer(): Serializer<V> = XmlSerializer(mapper)
        override fun deserializer(): Deserializer<V> = XmlDeserializer(mapper)
    }
}

class XmlSerializer<T : Any>(private val mapper: XMLMapper<T>) : Serializer<T> {
    override fun serialize(topic: String, data: T?): ByteArray? {
        return data?.let {
            mapper.writeValueAsBytes(data)
        }
    }
}

class XmlDeserializer<T : Any>(private val mapper: XMLMapper<T>) : Deserializer<T> {
    override fun deserialize(topic: String, data: ByteArray?): T? {
        return data?.let {
            mapper.readValue(data)
        }
    }
}
