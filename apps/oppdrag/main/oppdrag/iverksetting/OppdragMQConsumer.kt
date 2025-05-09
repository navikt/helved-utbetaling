package oppdrag.iverksetting

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.mq.*
import libs.postgres.Jdbc
import libs.postgres.concurrency.transaction
import libs.utils.*
import libs.xml.XMLMapper
import models.kontrakter.oppdrag.OppdragStatus
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import oppdrag.OppdragConfig
import oppdrag.utbetaling.*
import oppdrag.iverksetting.domene.kvitteringstatus
import oppdrag.iverksetting.domene.status
import oppdrag.iverksetting.tilstand.OppdragLagerRepository
import oppdrag.iverksetting.tilstand.id
import javax.jms.TextMessage

class OppdragMQConsumer(
    config: OppdragConfig,
    mq: MQ,
): AutoCloseable {
    private val mapper: XMLMapper<Oppdrag> = XMLMapper()
    private val consumer = DefaultMQConsumer(mq, config.kvitteringsKø, ::onMessage)

    fun onMessage(message: TextMessage) {
        mqLog.info("Mottok melding på kvitteringskø")
        secureLog.info("Mottok melding på kvitteringskø: ${message.text}")
        val kvittering = mapper.readValue(leggTilNamespacePrefiks(message.text))
        val oppdragIdKvittering = kvittering.id

        mqLog.debug("Henter oppdrag {} fra databasen", oppdragIdKvittering)

        mqLog.info(
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
            mqLog.warn("Oppdraget tilknyttet mottatt kvittering har uventet status i databasen. Oppdraget er: $oppdragIdKvittering")
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

    fun start() {
        consumer.start()
    }

    override fun close() {
        consumer.close()
    }
}
