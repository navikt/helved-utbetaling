package libs.xml

import java.io.StringReader
import java.io.StringWriter
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBElement
import javax.xml.namespace.QName
import javax.xml.stream.XMLInputFactory
import javax.xml.transform.stream.StreamSource
import kotlin.reflect.KClass

class XMLMapper<T : Any>(private val type: KClass<T>) {
    private val context = JAXBContext.newInstance(type.java)
    private val marshaller = context.createMarshaller()
    private val unmarshaller = context.createUnmarshaller()
    private val inputFactory = XMLInputFactory.newInstance()

    companion object {
        inline operator fun <reified T : Any> invoke(): XMLMapper<T> {
            return XMLMapper(T::class)
        }
    }

    fun readValue(value: String): T {
        val jaxb = StringReader(value).use { sr ->
            val reader = inputFactory.createXMLStreamReader(StreamSource(sr))
            val jaxb = unmarshaller.unmarshal(reader, type.java)
            reader.close()
            jaxb
        }

        return jaxb.value
    }

    fun writeValueAsString(value: T): String {
        val stringWriter = StringWriter()
        marshaller.marshal(value, stringWriter)
        return stringWriter.toString()
    }

    fun writeValueAsString(value: JAXBElement<T>): String {
        val stringWriter = StringWriter()
        marshaller.marshal(value, stringWriter)
        return stringWriter.toString()
    }

    /**
     * Uten xjc bindings i jaxb hvor man mangler @XMLRootElement,
     * kan man wrappe xmlen i en JAXBElement
     * ref [stackoverflow](https://stackoverflow.com/a/5870064)
     */
    fun wrapInTag(value: T, namespace: QName): JAXBElement<T> {
        return JAXBElement(namespace, type.java, value)
    }
}
