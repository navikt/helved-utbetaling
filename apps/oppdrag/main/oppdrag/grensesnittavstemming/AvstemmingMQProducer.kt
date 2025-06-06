package oppdrag.grensesnittavstemming

import libs.mq.*
import libs.utils.secureLog
import libs.xml.XMLMapper
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import oppdrag.AvstemmingConfig

class AvstemmingMQProducer(
    mq: MQ,
    private val config: AvstemmingConfig,
) {

    private val producer = DefaultMQProducer(mq, config.utKø)
    private val mapper: XMLMapper<Avstemmingsdata> = XMLMapper()

    fun sendGrensesnittAvstemming(avstemmingsdata: Avstemmingsdata) {
        if (!config.enabled) {
            mqLog.info("MQ-integrasjon mot oppdrag er skrudd av. Kan ikke sende avstemming")
            throw UnsupportedOperationException("Kan ikke sende avstemming til oppdrag. Integrasjonen er skrudd av.")
        }

        runCatching {
            mqLog.info("Sender avstemming til oppdrag")
            val xml = mapper.writeValueAsString(avstemmingsdata)
            producer.produce(xml)
        }.onFailure {
            mqLog.error("Klarte ikke sende avstemming til OS")
            secureLog.error("Klarte ikke sende avstemming til OS", it)
        }.getOrThrow()
    }
}
