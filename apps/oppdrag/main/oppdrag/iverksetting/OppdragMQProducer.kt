package oppdrag.iverksetting

import com.ibm.mq.jms.MQQueue
import libs.mq.MQ
import libs.mq.DefaultMQProducer
import libs.utils.secureLog
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.OppdragConfig
import oppdrag.appLog

class OppdragMQProducer(
    private val config: OppdragConfig,
    mq: MQ,
) {
    private val kvitteringQueue = MQQueue(config.kvitteringsKø)
    private val producer = DefaultMQProducer(mq, config.sendKø)
    private val mapper: XMLMapper<Oppdrag> = XMLMapper()

    fun sendOppdrag(oppdrag: Oppdrag): String {
        if (!config.enabled) {
            appLog.info("MQ-integrasjon mot oppdrag er skrudd av")
            error("Kan ikke sende melding til oppdrag. Integrasjonen er skrudd av.")
        }

        val oppdragId = oppdrag.oppdrag110?.oppdragsLinje150s?.lastOrNull()?.henvisning
        val oppdragXml = mapper.writeValueAsString(oppdrag)

        appLog.info("""Sender oppdrag til Oppdragsystemet
            fagsystem=${oppdrag.oppdrag110.kodeFagomraade}
            fagsak=${oppdrag.oppdrag110.fagsystemId} 
            behandling=$oppdragId 
            """.trimIndent())

        secureLog.info("""Sender oppdrag til Oppdragsystemet 
            fagsystem=${oppdrag.oppdrag110.kodeFagomraade}
            fagsak=${oppdrag.oppdrag110.fagsystemId} 
            behandling=$oppdragId 
            
            $oppdragXml
            """.trimIndent())

        runCatching {
            producer.produce(oppdragXml) {
                jmsReplyTo = kvitteringQueue
            }
        }.onFailure {
            // TODO: sjekk om feil er relatert til MQ-utilgjengelighet
            appLog.error("Klarte ikke sende Oppdrag til OS.")
            secureLog.error("Klarte ikke sende Oppdrag til OS. Feil: ", it)
        }.getOrThrow()

        return oppdrag.oppdrag110.fagsystemId
    }
}
