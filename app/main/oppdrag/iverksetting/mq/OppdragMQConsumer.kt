package oppdrag.iverksetting.mq

import libs.mq.MQConsumer
import libs.utils.appLog
import libs.utils.secureLog
import libs.xml.XMLMapper
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.Config
import oppdrag.iverksetting.domene.kvitteringstatus
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import oppdrag.iverksetting.tilstand.id
import oppdrag.postgres.transaction
import javax.jms.JMSException
import javax.jms.Message
import javax.jms.TextMessage
import javax.sql.DataSource

class OppdragMQConsumer(
    config: Config,
    private val postgres: DataSource,
    private val mapper: XMLMapper<Oppdrag> = XMLMapper(),
) : MQConsumer(config.mq, config.oppdrag.kvitteringsKø) {

    // todo: how to do session transactions on a consumer?
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
        appLog.error("Feil ved lesing av melding fra MQ ErrorCode: ${exception.errorCode}")
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
        val kvittering = mapper.readValue(leggTilNamespacePrefiks(melding.text))
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
}