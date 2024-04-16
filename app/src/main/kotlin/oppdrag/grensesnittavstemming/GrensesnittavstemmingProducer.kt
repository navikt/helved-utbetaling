package oppdrag.grensesnittavstemming

import libs.utils.appLog
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import oppdrag.AvstemmingConfig
import oppdrag.iverksetting.mq.MQProducer
import java.io.StringWriter
import javax.jms.Connection
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller

class GrensesnittavstemmingProducer(
    private val config: AvstemmingConfig,
) : MQProducer {
    private val context: JAXBContext = JAXBContext.newInstance(Avstemmingsdata::class.java)

    fun sendGrensesnittAvstemming(avstemmingsdata: Avstemmingsdata) {
        if (!config.enabled) {
            appLog.info("MQ-integrasjon mot oppdrag er skrudd av. Kan ikke sende avstemming")
            throw UnsupportedOperationException("Kan ikke sende avstemming til oppdrag. Integrasjonen er skrudd av.")
        }
//
//        try {
//            val destination = "queue:///${jmsTemplate.defaultDestinationName}?targetClient=1"
//            val melding = tilXml(avstemmingsdata)
//            jmsTemplate.convertAndSend(destination, melding)
//        } catch (e: JmsException) {
//            logger.error("Klarte ikke sende avstemming til OS. Feil:", e)
//            throw e
//        }
    }

    private fun tilXml(avstemming: Avstemmingsdata) =
        StringWriter().also { stringWriter ->
            context.createMarshaller().apply {
                setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
            }.marshal(avstemming, stringWriter)
        }.toString()

    override fun send(xml: String, con: Connection) {
        TODO("Not yet implemented")
    }
}
