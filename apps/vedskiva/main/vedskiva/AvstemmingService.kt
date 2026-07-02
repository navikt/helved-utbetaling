package vedskiva

import libs.jdbc.concurrency.transaction
import libs.kafka.KafkaProducer
import libs.utils.appLog
import models.*
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import java.time.LocalDateTime
import java.util.UUID

class AvstemmingService(val producer: KafkaProducer<String, Avstemmingsdata>) {
    suspend fun avstemminger(
        avstemFom: LocalDateTime,
        avstemTom: LocalDateTime,
    ): List<Pair<String, List<Avstemmingsdata>>> {
        val daos = transaction {
            OppdragDao.selectWith(avstemFom, avstemTom)
        }
        return daos
            .groupBy { it.kodeFagomraade }
            .map { (kodeFagomraade, daos) ->
                val fagsystem = Fagsystem.fromFagområde(kodeFagomraade)
                appLog.debug("oppretter oppdragsdata for {}", fagsystem)

                val avstemmingId = AvstemmingFactory.genererId()
                val oppdragsdatas = daos.map { dao ->
                    Oppdragsdata(
                        fagsystem = fagsystem,
                        personident = Personident(dao.personident),
                        sakId = SakId(dao.fagsystemId),
                        lastDelytelseId = dao.lastDelytelseId,
                        innsendt = dao.tidspktMelding,
                        totalBeløpAllePerioder = dao.sats.toUInt(),
                        kvittering = dao.alvorlighetsgrad?.let {
                            Kvittering(
                                alvorlighetsgrad = dao.alvorlighetsgrad,
                                kode = dao.kodeMelding,
                                melding = dao.beskrMelding,
                            )
                        },
                    )
                }
                val avstemming = Avstemming(avstemmingId, avstemFom, avstemTom, oppdragsdatas)
                val messages = AvstemmingFactory.create(avstemming)
                kodeFagomraade to messages
            }
    }

    suspend fun avstem(fom: LocalDateTime, tom: LocalDateTime) {
        avstemminger(fom, tom).forEach { (fagområde, messages) ->
            messages.forEach { message ->
                // FIXME: hvis forrige iter i forEach gikk bra, men neste feiler. Så har vi allerede sendt ut disse
                // Hvordan kan vi gjøre alle forEach (fagområde, daos) atomisk?
                producer.send(UUID.randomUUID().toString(), message, 0)
            }
            appLog.info("Avstemming for $fagområde completed with avstemmingId: ${messages.first().aksjon.avleverendeAvstemmingId}")
        }
    }
}