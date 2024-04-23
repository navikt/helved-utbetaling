package oppdrag.grensesnittavstemming

import com.ibm.mq.jms.MQQueue
import libs.mq.MQ
import libs.mq.MQProducer
import libs.utils.appLog
import libs.utils.secureLog
import libs.xml.XMLMapper
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import oppdrag.AvstemmingConfig
import javax.xml.namespace.QName

class GrensesnittavstemmingProducer(
    mq: MQ,
    private val config: AvstemmingConfig,
) {
    private val queue = MQQueue(config.utKø).apply {
        targetClient = 1 // Skru av JMS-headere, da OS ikke støtter disse for avstemming
    }
    private val producer = MQProducer(mq, queue)
    private val mapper: XMLMapper<Avstemmingsdata> = XMLMapper()

    fun sendGrensesnittAvstemming(avstemmingsdata: Avstemmingsdata) {
        if (!config.enabled) {
            appLog.info("MQ-integrasjon mot oppdrag er skrudd av. Kan ikke sende avstemming")
            throw UnsupportedOperationException("Kan ikke sende avstemming til oppdrag. Integrasjonen er skrudd av.")
        }

        runCatching {
            appLog.info("Sender avstemming til oppdrag")
            val rootTag = mapper.wrapInTag(avstemmingsdata, QName("uri", "local"))
            val xml = mapper.writeValueAsString(rootTag)
            producer.produce(xml)
        }.onFailure {
            appLog.error("Klarte ikke sende avstemming til OS")
            secureLog.error("Klarte ikke sende avstemming til OS", it)
        }.getOrThrow()
    }
}
