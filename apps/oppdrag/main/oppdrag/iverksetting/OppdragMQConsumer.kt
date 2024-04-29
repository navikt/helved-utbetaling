package oppdrag.iverksetting

import com.ibm.mq.jms.MQQueue
import libs.mq.MQ
import libs.mq.MQConsumer
import libs.postgres.transaction
import libs.utils.appLog
import libs.xml.XMLMapper
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.OppdragConfig
import oppdrag.iverksetting.domene.kvitteringstatus
import oppdrag.iverksetting.domene.status
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import oppdrag.iverksetting.tilstand.id
import javax.jms.TextMessage
import javax.sql.DataSource

class OppdragMQConsumer(
    config: OppdragConfig,
    mq: MQ,
    private val postgres: DataSource,
    private val mapper: XMLMapper<Oppdrag> = XMLMapper(),
) : MQConsumer(mq, MQQueue(config.kvitteringsKø)) {

    override fun onMessage(message: TextMessage) {
        val kvittering = mapper.readValue(leggTilNamespacePrefiks(message.text))
        val oppdragIdKvittering = kvittering.id

        if (kvittering.oppdrag110.fagsystemId == "denne_skal_sprenge") {
            error("Boom!")
        }

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
                OppdragLagerRepository.hentAlleVersjonerAvOppdrag(oppdragIdKvittering, con)
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
                kvittering.status,
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