package libs.kafka

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.reflect.KClass
import libs.xml.*
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.streams.kstream.WindowedSerdes
import org.apache.kafka.streams.kstream.Windowed

data class Serdes<K: Any, V>(
    val key: StreamSerde<K>, 
    val value: StreamSerde<V>,
)

interface StreamSerde<T> : Serde<T>

fun string() = Serdes(StringSerde, StringSerde)
fun bytes() = Serdes(StringSerde, ByteArraySerde)
inline fun <reified V: Any> json() = Serdes(StringSerde, JsonSerde.jackson<V>())
inline fun <reified V: Any> jsonList() = Serdes(StringSerde, JsonSerde.jacksonList<V>())
inline fun <reified V: Any> xml() = Serdes(StringSerde, XmlSerde.xml<V>())
inline fun <reified V: Any> jaxb() = Serdes(StringSerde, XmlSerde.jaxb<V>())
inline fun <reified K : Any, reified V : Any> jsonjson() = Serdes(JsonSerde.jackson<K>(), JsonSerde.jackson<V>())
inline fun <reified K : Any, reified V : Any> jsonjsonList() = Serdes(JsonSerde.jackson<K>(), JsonSerde.jacksonList<V>())
inline fun <reified K : Any, reified V : Any> jsonjsonSet() = Serdes(JsonSerde.jackson<K>(), JsonSerde.jacksonSet<V>())
inline fun <reified V : Any> windowedjsonList() = Serdes(WindowedStringSerde, JsonSerde.jacksonList<V>())

object WindowedStringSerde: StreamSerde<Windowed<String>> {
    private val internalSerde: Serde<Windowed<String>> = WindowedSerdes.sessionWindowedSerdeFrom(String::class.java)
    override fun serializer(): Serializer<Windowed<String>> = internalSerde.serializer()
    override fun deserializer(): Deserializer<Windowed<String>> = internalSerde.deserializer()

}
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
    inline fun <reified V: Any> jacksonList(): StreamSerde<List<V>> = object: StreamSerde<List<V>> {
        override fun serializer(): Serializer<List<V>> = JacksonSerializer()
        override fun deserializer(): Deserializer<List<V>> = JacksonListDeserializer(V::class)
    }
    inline fun <reified V: Any> jacksonSet(): StreamSerde<Set<V>> = object: StreamSerde<Set<V>> {
        override fun serializer(): Serializer<Set<V>> = JacksonSerializer()
        override fun deserializer(): Deserializer<Set<V>> = JacksonSetDeserializer(V::class)
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

class JacksonListDeserializer<T: Any>(private val klass: KClass<T>): Deserializer<List<T>> {
    private val type: JavaType = JsonSerde.jackson.typeFactory.constructCollectionType(List::class.java, klass.java)
    override fun deserialize(topic: String, data: ByteArray?): List<T>? {
        if (data == null) return null
        return JsonSerde.jackson.readValue(data, type)
    }
}

class JacksonSetDeserializer<T: Any>(private val klass: KClass<T>): Deserializer<Set<T>> {
    private val type: JavaType = JsonSerde.jackson.typeFactory.constructCollectionType(Set::class.java, klass.java)
    override fun deserialize(topic: String, data: ByteArray?): Set<T>? {
        if (data == null) return null
        return JsonSerde.jackson.readValue(data, type)
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
