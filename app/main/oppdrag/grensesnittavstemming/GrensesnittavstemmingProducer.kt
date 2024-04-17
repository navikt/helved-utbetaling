package oppdrag.grensesnittavstemming

import com.ibm.mq.jms.MQConnectionFactory
import libs.utils.appLog
import libs.utils.secureLog
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import oppdrag.Config
import oppdrag.mq.MQProducer
import java.io.StringWriter
import javax.jms.Connection
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller

class GrensesnittavstemmingProducer(
    private val config: Config,
    private val factory: MQConnectionFactory,
) : MQProducer {
    private val context: JAXBContext = JAXBContext.newInstance(Avstemmingsdata::class.java)
//    private val queueName = "queue:///${config.utKø}?targetClient=1"

    fun sendGrensesnittAvstemming(avstemmingsdata: Avstemmingsdata) {
        if (!config.avstemming.enabled) {
            appLog.info("MQ-integrasjon mot oppdrag er skrudd av. Kan ikke sende avstemming")
            throw UnsupportedOperationException("Kan ikke sende avstemming til oppdrag. Integrasjonen er skrudd av.")
        }

        runCatching {
            factory.createConnection(config.mq.username, config.mq.password).use { con ->
                val xml = xml(avstemmingsdata)
                send(xml, con)
            }
        }.onFailure {
            appLog.error("Klarte ikke sende avstemming til OS")
            secureLog.error("Klarte ikke sende avstemming til OS", it)
        }.getOrThrow()
    }

    private fun xml(avstemming: Avstemmingsdata): String {
        val stringWriter = StringWriter()

        val marshaller = context.createMarshaller().apply {
            setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
        }

        return marshaller
            .marshal(avstemming, stringWriter)
            .toString()
    }

    override fun send(xml: String, con: Connection) {
        con.createSession().use { session ->
            session.createProducer(session.createQueue(config.avstemming.utKø)).use { producer ->
                val msg = session.createTextMessage(xml)
                producer.send(msg)
            }
        }
    }
}
