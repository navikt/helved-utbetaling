package oppdrag.iverksetting.mq

import no.trygdeetaten.skjema.oppdrag.Oppdrag
import java.io.StringReader
import java.io.StringWriter
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.stream.XMLInputFactory
import javax.xml.transform.stream.StreamSource

internal object OppdragXmlMapper {
    private val jaxbContext: JAXBContext = JAXBContext.newInstance(Oppdrag::class.java)
    private val xmlInputFactory = XMLInputFactory.newInstance()

    fun tilOppdrag(oppdragXml: String): Oppdrag {
        val oppdrag =
            jaxbContext.createUnmarshaller().unmarshal(
                xmlInputFactory.createXMLStreamReader(StreamSource(StringReader(oppdragXml))),
                Oppdrag::class.java,
            )

        return oppdrag.value
    }

    fun tilXml(oppdrag: Oppdrag): String {
        val stringWriter = StringWriter()
        val marshaller =
            jaxbContext.createMarshaller().apply {
                setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
            }
        marshaller.marshal(oppdrag, stringWriter)
        return stringWriter.toString()
    }
}
