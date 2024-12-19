package oppdrag.iverksetting

import com.ibm.mq.jms.MQQueue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.mq.MQ
import libs.mq.MQConsumer
import libs.postgres.Jdbc
import libs.postgres.concurrency.transaction
import libs.utils.secureLog
import libs.xml.XMLMapper
import no.nav.utsjekk.kontrakter.oppdrag.OppdragStatus
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.OppdragConfig
import oppdrag.appLog
import oppdrag.utbetaling.*
import oppdrag.iverksetting.domene.kvitteringstatus
import oppdrag.iverksetting.domene.status
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import oppdrag.iverksetting.tilstand.id
import javax.jms.TextMessage

class OppdragMQConsumer(
    config: OppdragConfig,
    mq: MQ,
    private val mapper: XMLMapper<Oppdrag> = XMLMapper(),
) : MQConsumer(mq, MQQueue(config.kvitteringsKø)) {

    override fun onMessage(message: TextMessage) {
        secureLog.info("Mottok melding på kvitteringskø: ${message.text}")
        val kvittering = mapper.readValue(leggTilNamespacePrefiks(message.text))
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

        val førsteOppdragUtenKvittering = runBlocking {
            withContext(Jdbc.context) {
                transaction {
                    OppdragLagerRepository
                        .hentAlleVersjonerAvOppdrag(oppdragIdKvittering)
                        .find { lager -> lager.status == OppdragStatus.LAGT_PÅ_KØ }
                }
            }
        }

        // TODO: lagre alle kvitteringer i egen tabell
        val førsteUtbetalingsoppdragUtenKvittering = runBlocking {
                withContext(Jdbc.context) {
                    transaction {
                        UtbetalingDao.findOrNull(oppdragIdKvittering)
                        .find { utbet -> utbet.status == OppdragStatus.LAGT_PÅ_KØ }
                    }
                }
            }

        if (førsteOppdragUtenKvittering == null && førsteUtbetalingsoppdragUtenKvittering == null) {
            appLog.warn("Oppdraget tilknyttet mottatt kvittering har uventet status i databasen. Oppdraget er: $oppdragIdKvittering")
            return
        }

        førsteOppdragUtenKvittering?.id?.let { oppdragId ->
            if (kvittering.mmel != null) {
                runBlocking {
                    withContext(Jdbc.context) {
                        transaction {
                            OppdragLagerRepository.oppdaterKvitteringsmelding(
                                oppdragId,
                                kvittering.mmel,
                                førsteOppdragUtenKvittering.versjon,
                            )
                        }
                    }
                }
            }

            runBlocking {
                withContext(Jdbc.context) {
                    transaction {
                        OppdragLagerRepository.oppdaterStatus(
                            oppdragId,
                            kvittering.status,
                            førsteOppdragUtenKvittering.versjon
                        )
                    }
                }
            }
        } 

        førsteUtbetalingsoppdragUtenKvittering?.let { utbetDao ->
            runBlocking {
                withContext(Jdbc.context) {
                    transaction {
                        utbetDao
                            .copy(
                                kvittering = kvittering.mmel ?: utbetDao.kvittering, 
                                status = kvittering.status
                            )
                            .update()
                    }
                }
            }
        }

    }

    private fun leggTilNamespacePrefiks(xml: String): String {
        return xml
            .replace("<oppdrag xmlns=", "<ns2:oppdrag xmlns:ns2=", ignoreCase = true)
            .replace("</oppdrag>", "</ns2:oppdrag>", ignoreCase = true)
    }
}
