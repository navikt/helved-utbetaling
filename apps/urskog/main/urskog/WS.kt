package urskog

import io.ktor.client.plugins.logging.*
import jakarta.xml.bind.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import javax.xml.namespace.QName
import javax.xml.stream.XMLInputFactory
import javax.xml.transform.stream.StreamSource
import kotlin.reflect.KClass
import libs.auth.AzureTokenProvider
import libs.http.HttpClientFactory
import libs.kafka.StreamSerde
import libs.ws.*
import libs.kafka.*
import libs.xml.XMLMapper
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.ObjectFactory
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer

private object SimulerAction {
    private const val HOST = "http://nav.no"
    private const val PATH = "system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt"
    private const val SERVICE = "simulerFpService"
    const val BEREGNING = "$HOST/$PATH/$SERVICE/simulerBeregning"
    const val SEND_OPPDRAG = "$HOST/$PATH/$SERVICE/sendInnOppdragRequest"
}

class SimuleringService(
    private val config: Config,
) {
    private val http = HttpClientFactory.new(LogLevel.ALL)
    private val azure = AzureTokenProvider(config.azure)
    private val sts = StsClient(config.simulering.sts, http, proxyAuth = ::getAzureToken)
    private val soap = SoapClient(config.simulering, sts, http, proxyAuth = ::getAzureToken)
    private val from: XMLMapper<SimulerBeregningResponse> = XMLMapper()

    suspend fun simuler(request: SimulerBeregningRequest): SimulerBeregningResponse  {
        val response = soap.call(SimulerAction.BEREGNING, request.into())
        return from.readValue(response)
    }

    private suspend fun getAzureToken(): String {
        return "Bearer ${azure.getClientCredentialsToken(config.proxy.scope).access_token}"
    }
}

private fun SimulerBeregningRequest.into(): String {
    val q = QName("http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt", "simulerBeregningRequest", "ns3")
    val root = JAXBElement(q, SimulerBeregningRequest::class.java, this)
    val ctx = JAXBContext.newInstance(SimulerBeregningRequest::class.java, ObjectFactory::class.java)
    val marshaller = ctx.createMarshaller().apply {
        setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
        setProperty(Marshaller.JAXB_FRAGMENT, true)
    }
    val writer = StringWriter()
    marshaller.marshal(root, writer)
    return writer.toString()
}

inline fun <reified V: Any, reified O: Any> jaxb(): Serdes<String, V> {
    return Serdes(StringSerde, JaxbSerde.serde<V, O>())
}

object JaxbSerde {
    inline fun <reified V : Any, reified O: Any> serde(): StreamSerde<V> = object : StreamSerde<V> {
        override fun serializer(): Serializer<V> = JaxbSerializer(V::class, O::class)
        override fun deserializer(): Deserializer<V> = JaxbDeserializer(V::class, O::class)
    }
}

class JaxbSerializer<T : Any, O: Any>(
    private val kclass: KClass<T>,
    objectFactory: KClass<O>,
) : Serializer<T> {
    private val ctx = JAXBContext.newInstance(kclass.java, objectFactory.java)
    private val marshaller = ctx.createMarshaller().apply { 
        setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
        setProperty(Marshaller.JAXB_FRAGMENT, true)
    }
    override fun serialize(topic: String, data: T?): ByteArray? {
        return data?.let {
            val q = QName("http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt", "simulerBeregningRequest", "ns3")
            val root = JAXBElement(q, kclass.java, data)
            val outStream = ByteArrayOutputStream()
            marshaller.marshal(root, outStream)
            outStream.toByteArray()
        }
    }
}

class JaxbDeserializer<T : Any, O: Any>(
    private val kclass: KClass<T>,
    objectFactory: KClass<O>,
) : Deserializer<T> {
    private val ctx = JAXBContext.newInstance(kclass.java, objectFactory.java)
    private val unmarshaller = ctx.createUnmarshaller()
    private val inputFactory = XMLInputFactory.newInstance()
    override fun deserialize(topic: String, data: ByteArray?): T? {
        return data?.let {
            val reader = inputFactory.createXMLStreamReader(StreamSource(ByteArrayInputStream(data)))
            val jaxb = unmarshaller.unmarshal(reader, kclass.java)
            reader.close()
            return jaxb.value
        }
    }
}

