package oppdrag.grensesnittavstemming

import felles.log.appLog
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import oppdrag.AvstemmingConfig
import java.io.StringWriter
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller

class GrensesnittavstemmingSender(
//    private val jmsTemplate: JmsTemplate,
    private val config: AvstemmingConfig,
) {
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
}
