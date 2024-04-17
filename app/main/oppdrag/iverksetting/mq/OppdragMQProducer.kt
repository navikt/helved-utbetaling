package oppdrag.iverksetting.mq

import com.ibm.mq.jms.MQQueue
import libs.utils.appLog
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.OppdragConfig
import oppdrag.mq.MQProducer
import javax.jms.Connection
import javax.jms.JMSException

class OppdragMQProducer(private val config: OppdragConfig) : MQProducer {

    override fun send(xml: String, con: Connection) {
        con.createSession().use { session ->
            // todo: check queue depth
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
        val oppdragXml = OppdragXmlMapper.tilXml(oppdrag)

        appLog.info(
            "Sender oppdrag for fagsystem=${oppdrag.oppdrag110.kodeFagomraade} og " +
                    "fagsak=${oppdrag.oppdrag110.fagsystemId} behandling=$oppdragId til Oppdragsystemet",
        )

        try {
            send(oppdragXml, con)
        } catch (e: JMSException) {
            // todo: skal vi sjekke om MQ er utilgjengelig?
            appLog.error("Klarte ikke sende Oppdrag til OS. Feil: ", e)
            throw e
        }

        return oppdrag.oppdrag110.fagsystemId
    }
}
