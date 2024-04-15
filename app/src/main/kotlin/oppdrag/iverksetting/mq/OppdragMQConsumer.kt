package oppdrag.iverksetting.mq

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.mq.jms.MQQueue
import felles.appLog
import felles.secureLog
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import oppdrag.OppdragConfig
import oppdrag.iverksetting.domene.kvitteringstatus
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import oppdrag.iverksetting.tilstand.id
import oppdrag.postgres.transaction
import javax.jms.*
import javax.sql.DataSource

interface MQConsumer : MessageListener, ExceptionListener, AutoCloseable {
    fun start()
}

class OppdragMQConsumer(
    config: OppdragConfig,
    private val postgres: DataSource,
    factory: MQConnectionFactory,
) : MQConsumer {

    private val queue = MQQueue(config.kvitteringsKø)
    private val connection = factory.createConnection(config.mq.username, config.mq.password).apply {
        exceptionListener = this@OppdragMQConsumer
    }
    private val session = connection.createSession().apply {
        createConsumer(queue).apply {
            messageListener = this@OppdragMQConsumer
        }
    }

    override fun start() {
        connection.start()
    }

    override fun onMessage(message: Message) {
        when (message) {
            is TextMessage -> tryBehandleMelding(message)
            else -> {
                appLog.error("Meldingstype er ikke støttet: ${message.jmsType}")
                secureLog.error("Melding (kvittering) kan ikke leses: $message")
            }
        }
    }

    override fun onException(exception: JMSException) {
        session.acknowledgeMode
        secureLog.error("Feil ved lesing av melding fra MQ", exception)
    }

    private fun tryBehandleMelding(message: TextMessage) {
        runCatching {
            behandleMelding(message)
        }.onFailure {
            appLog.error(
                """
                    Feilet lesing av kvitteringsmelding fra MQ
                        JMS ID: ${message.jmsMessageID}
                        Innhold: ${message.text}
                """.trimIndent()
            )
            secureLog.error("Feilet håndtering av melding", it)
        }
    }

    private fun behandleMelding(melding: TextMessage) {
        val kvittering = OppdragXmlMapper.tilOppdrag(leggTilNamespacePrefiks(melding.text))
        val oppdragIdKvittering = kvittering.id

        appLog.debug("Henter oppdrag {} fra databasen", oppdragIdKvittering)

        appLog.info(
            """
            Mottatt melding på kvitteringskø for 
                Fagsak: $oppdragIdKvittering 
                Status: ${kvittering.kvitteringstatus}
                Svar:   ${kvittering.mmel?.beskrMelding ?: "Beskrivende melding ikke satt fra OS"}
            """.trimIndent()
        )

        val førsteOppdragUtenKvittering =
            postgres.transaction { con ->
                OppdragLagerRepository
                    .hentAlleVersjonerAvOppdrag(oppdragIdKvittering, con)
                    .find { lager -> lager.status == OppdragStatus.LAGT_PÅ_KØ }
            }

        if (førsteOppdragUtenKvittering == null) {
            appLog.warn("Oppdraget tilknyttet mottatt kvittering har uventet status i databasen. Oppdraget er: $oppdragIdKvittering")
            return
        }
        val oppdragId = førsteOppdragUtenKvittering.id

        if (kvittering.mmel != null) {
            postgres.transaction { con ->
                OppdragLagerRepository.oppdaterKvitteringsmelding(
                    oppdragId,
                    kvittering.mmel,
                    con,
                    førsteOppdragUtenKvittering.versjon,
                )
            }
        }

        postgres.transaction { con ->
            OppdragLagerRepository.oppdaterStatus(
                oppdragId,
                OppdragStatus.KVITTERT_OK,
                con,
                førsteOppdragUtenKvittering.versjon
            )
        }
    }

    fun leggTilNamespacePrefiks(xml: String): String {
        return xml
            .replace("<oppdrag xmlns=", "<ns2:oppdrag xmlns:ns2=", ignoreCase = true)
            .replace("</oppdrag>", "</ns2:oppdrag>", ignoreCase = true)
    }

    override fun close() {
        session.close()
        connection.close()
    }
}