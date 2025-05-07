package oppdrag.iverksetting

import libs.mq.*
import libs.utils.secureLog
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.OppdragConfig

class OppdragMQProducer(
    private val config: OppdragConfig,
    mq: MQ,
) {
    private val kvitteringQueue = config.kvitteringsKø
    private val producer = DefaultMQProducer(mq, config.sendKø)
    private val mapper: XMLMapper<Oppdrag> = XMLMapper()

    fun sendOppdrag(oppdrag: Oppdrag): String {
        if (!config.enabled) {
            mqLog.warn("MQ-integrasjon mot oppdrag er skrudd av")
            error("Kan ikke sende melding til oppdrag. Integrasjonen er skrudd av.")
        }

        val oppdragId = oppdrag.oppdrag110?.oppdragsLinje150s?.lastOrNull()?.henvisning
        val oppdragXml = mapper.writeValueAsString(oppdrag)

        mqLog.info("""Sender oppdrag til Oppdragsystemet
            fagsystem=${oppdrag.oppdrag110.kodeFagomraade}
            fagsak=${oppdrag.oppdrag110.fagsystemId} 
            behandling=$oppdragId 
            """.trimIndent())

        secureLog.info("""Sender oppdrag til Oppdragsystemet 
            fagsystem=${oppdrag.oppdrag110.kodeFagomraade}
            fagsak=${oppdrag.oppdrag110.fagsystemId} 
            behandling=$oppdragId 
            xml=$oppdragXml
            """.trimIndent())

        runCatching {
            producer.produce(oppdragXml) {
                jmsReplyTo = kvitteringQueue
            }
        }.onFailure {
            // TODO: sjekk om feil er relatert til MQ-utilgjengelighet
            mqLog.error("Klarte ikke sende Oppdrag til OS.")
            secureLog.error("Klarte ikke sende Oppdrag til OS.", it)
        }.getOrThrow()

        return oppdrag.oppdrag110.fagsystemId
    }
}
