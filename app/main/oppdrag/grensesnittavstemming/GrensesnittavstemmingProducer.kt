package oppdrag.grensesnittavstemming

import com.ibm.mq.jms.MQConnectionFactory
import libs.mq.MQProducer
import libs.mq.transaction
import libs.utils.appLog
import libs.utils.secureLog
import libs.xml.XMLMapper
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import oppdrag.Config
import javax.jms.Connection
import javax.xml.namespace.QName

class GrensesnittavstemmingProducer(
    private val config: Config,
    private val mq: MQConnectionFactory,
) : MQProducer {
    private val mapper: XMLMapper<Avstemmingsdata> = XMLMapper()

    override fun send(xml: String, con: Connection) {
        con.transaction { session ->
            session.createProducer(session.createQueue(config.avstemming.utKø)).use { producer ->
                val msg = session.createTextMessage(xml)
                producer.send(msg)
            }
        }
    }

    fun sendGrensesnittAvstemming(avstemmingsdata: Avstemmingsdata) {
        if (!config.avstemming.enabled) {
            appLog.info("MQ-integrasjon mot oppdrag er skrudd av. Kan ikke sende avstemming")
            throw UnsupportedOperationException("Kan ikke sende avstemming til oppdrag. Integrasjonen er skrudd av.")
        }

        runCatching {
            mq.createConnection(config.mq.username, config.mq.password).use { con ->
                val rootTag = mapper.wrapInTag(avstemmingsdata, QName("uri", "local"))
                val xml = mapper.writeValueAsString(rootTag)
                send(xml, con)
            }
        }.onFailure {
            appLog.error("Klarte ikke sende avstemming til OS")
            secureLog.error("Klarte ikke sende avstemming til OS", it)
        }.getOrThrow()
    }
}
