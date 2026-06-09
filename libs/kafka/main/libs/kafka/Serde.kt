package libs.kafka

import libs.utils.secureLog
import kotlin.reflect.KClass
import libs.xml.*
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.streams.kstream.WindowedSerdes
import org.apache.kafka.streams.kstream.Windowed
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.json.Json

data class Serdes<K: Any, V>(
    val key: StreamSerde<K>, 
    val value: StreamSerde<V>,
)

interface StreamSerde<T> : Serde<T>

object Serde {
    inline fun <reified V: Any> xml() = XmlSerde.xml<V>()
    inline fun <reified V: Any> json() = JsonSerde.kotlinx<V>()
    inline fun <reified V: Any> listStreamsPair() = JsonSerde.listStreamsPair<V, V?>()
    inline fun <reified V: Any> streamsPair() = JsonSerde.streamsPair<V, V?>()
    fun string() = StringSerde
}

fun string() = Serdes(StringSerde, StringSerde)
fun bytes() = Serdes(StringSerde, ByteArraySerde)
inline fun <reified V: Any> json() = Serdes(StringSerde, JsonSerde.kotlinx<V>())
inline fun <reified V: Any> jsonList() = Serdes(StringSerde, JsonSerde.jacksonList<V>())
inline fun <reified V: Any> jsonListStreamsPair() = Serdes(StringSerde, JsonSerde.listStreamsPair<V, V?>())
inline fun <reified V: Any> jsonStreamsPair() = Serdes(StringSerde, JsonSerde.streamsPair<V, V?>())
inline fun <reified V: Any> xml() = Serdes(StringSerde, XmlSerde.xml<V>())
inline fun <reified V: Any> jaxb() = Serdes(StringSerde, XmlSerde.jaxb<V>())
inline fun <reified K: Any> jsonString() = Serdes(JsonSerde.kotlinx<K>(), StringSerde)
inline fun <reified K : Any, reified V : Any> jsonjson() = Serdes(JsonSerde.kotlinx<K>(), JsonSerde.kotlinx<V>())
inline fun <reified K : Any, reified V : Any> jsonjsonList() = Serdes(JsonSerde.kotlinx<K>(), JsonSerde.jacksonList<V>())
inline fun <reified K : Any, reified V : Any> jsonjsonSet() = Serdes(JsonSerde.kotlinx<K>(), JsonSerde.jacksonSet<V>())
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
    val json = Json { ignoreUnknownKeys = true }

    inline fun <reified V : Any> kotlinx(): StreamSerde<V> = object : StreamSerde<V> {
        override fun serializer(): Serializer<V> = KotlinxSerializer(serializer<V>())
        override fun deserializer(): Deserializer<V> = KotlinxDeserializer(serializer<V>())
    }
    inline fun <reified V: Any> jacksonList(): StreamSerde<List<V>> = object: StreamSerde<List<V>> {
        override fun serializer(): Serializer<List<V>> = KotlinxSerializer(ListSerializer(serializer<V>()))
        override fun deserializer(): Deserializer<List<V>> = KotlinxDeserializer(ListSerializer(serializer<V>()))
    }
    inline fun <reified V: Any> jacksonSet(): StreamSerde<Set<V>> = object: StreamSerde<Set<V>> {
        override fun serializer(): Serializer<Set<V>> = KotlinxSerializer(SetSerializer(serializer<V>()))
        override fun deserializer(): Deserializer<Set<V>> = KotlinxDeserializer(SetSerializer(serializer<V>()))
    }
    inline fun <reified L: Any, reified R> listStreamsPair(): StreamSerde<List<StreamsPair<L, R>>> {
        val ser = ListSerializer(StreamsPair.serializer(serializer<L>(), serializer<R>()))
        return object: StreamSerde<List<StreamsPair<L, R>>> {
            override fun serializer(): Serializer<List<StreamsPair<L, R>>> = KotlinxSerializer(ser)
            override fun deserializer(): Deserializer<List<StreamsPair<L, R>>> = KotlinxDeserializer(ser)
        }
    }

    inline fun <reified L: Any, reified R> streamsPair(): StreamSerde<StreamsPair<L, R>> {
        val ser = StreamsPair.serializer(serializer<L>(), serializer<R>())
        return object: StreamSerde<StreamsPair<L, R>> {
            override fun serializer(): Serializer<StreamsPair<L, R>> = KotlinxSerializer(ser)
            override fun deserializer(): Deserializer<StreamsPair<L, R>> = KotlinxDeserializer(ser)
        }
    }
}

class KotlinxSerializer<T>(private val kSerializer: KSerializer<T>) : Serializer<T> {
    override fun serialize(_topic: String, data: T?): ByteArray? {
        return data?.let {
            JsonSerde.json.encodeToString(kSerializer, data).toByteArray()
        }
    }
}

class KotlinxDeserializer<T>(private val kSerializer: KSerializer<T>) : Deserializer<T> {
    override fun deserialize(topic: String, data: ByteArray?): T? {
        if (data == null) return null
        val rawJson = String(data, Charsets.UTF_8)
        try {
            return JsonSerde.json.decodeFromString(kSerializer, rawJson)
        } catch (e: Exception) {
            secureLog.warn("Deserialization failed on topic $topic. Raw data: $rawJson")
            throw e
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
        if (data == null) return null
        try {
            return mapper.readValue(data)
        } catch (e: Exception) {
            val rawXml = String(data, Charsets.UTF_8)
            secureLog.warn("Deserialization failed on topic $topic. Raw data: $rawXml")
            throw e
        }
    }
}
