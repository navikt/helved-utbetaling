package oppdrag.iverksetting.mq

import com.ibm.mq.jms.MQQueue
import libs.mq.MQProducer
import libs.mq.transaction
import libs.utils.appLog
import libs.utils.secureLog
import libs.xml.XMLMapper
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.OppdragConfig
import javax.jms.Connection

class OppdragMQProducer(private val config: OppdragConfig) : MQProducer {
    private val mapper: XMLMapper<Oppdrag> = XMLMapper()

    override fun send(xml: String, con: Connection) {
        con.transaction { session ->
            session.createProducer(session.createQueue(config.sendKø)).use { producer ->
                val msg = session.createTextMessage(xml)
                msg.jmsReplyTo = MQQueue(config.kvitteringsKø)
                producer.send(msg)
            }
        }
    }

    fun sendOppdrag(oppdrag: Oppdrag, con: Connection): String {
        if (!config.enabled) {
            appLog.info("MQ-integrasjon mot oppdrag er skrudd av")
            error("Kan ikke sende melding til oppdrag. Integrasjonen er skrudd av.")
        }

        val oppdragId = oppdrag.oppdrag110?.oppdragsLinje150?.lastOrNull()?.henvisning
        val oppdragXml = mapper.writeValueAsString(oppdrag)

        appLog.info(
            "Sender oppdrag for fagsystem=${oppdrag.oppdrag110.kodeFagomraade} og " +
                    "fagsak=${oppdrag.oppdrag110.fagsystemId} behandling=$oppdragId til Oppdragsystemet",
        )

        runCatching {
            send(oppdragXml, con)
        }.onFailure {
            // TODO: sjekk om feil er relatert til MQ-utilgjengelighet
            appLog.error("Klarte ikke sende Oppdrag til OS.")
            secureLog.error("Klarte ikke sende Oppdrag til OS. Feil: ", it)
        }.getOrThrow()

        return oppdrag.oppdrag110.fagsystemId
    }
}
