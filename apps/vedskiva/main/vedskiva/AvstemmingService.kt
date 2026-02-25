package vedskiva

import libs.jdbc.concurrency.transaction
import libs.kafka.KafkaProducer
import libs.utils.appLog
import models.*
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AvstemmingService(
    config: Config,
    val producer: KafkaProducer<String, Avstemmingsdata>,
    val peisschtappern: PeisschtappernClient = PeisschtappernClient(config)
) {

    suspend fun generate2(
        avstemFom: LocalDateTime,
        avstemTom: LocalDateTime,
    ): List<Pair<String, List<Avstemmingsdata>>> {
        val daos = transaction {
            OppdragDao.selectWith(avstemFom, avstemTom)
        }
        return daos.asSequence()
            .distinctBy { dao -> dao.hashKey to dao.alvorlighetsgrad }
            .groupBy { it.hashKey }
            .map { (_, value) -> value.firstOrNull { it.alvorlighetsgrad != null} ?: value.first() }
            .groupBy { it.kodeFagomraade }
            .map { (kodeFagomraade, daos) ->
                val fagsystem = Fagsystem.fromFagområde(kodeFagomraade)
                appLog.debug("oppretter oppdragsdata for $fagsystem")

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

    suspend fun generate(
        avstemFom: LocalDateTime,
        avstemTom: LocalDateTime,
    ): List<Pair<String, List<Avstemmingsdata>>> {
        val oppdragDaos = mutableMapOf<String, Set<Dao>>()

        peisschtappern.oppdrag(
            fom = avstemFom,
            tom = avstemTom,
        ).also {
            appLog.info("Fetched ${it.size} oppdrag between $avstemFom - $avstemTom from peisschtappern")
        }.filter { dao ->
            val avstemmingdag: LocalDateTime? = dao.oppdrag?.let { oppdrag ->
                oppdrag.oppdrag110?.avstemming115?.tidspktMelding?.trimEnd()
                    ?.let { LocalDateTime.parse(it, formatter) } 
                    // hvis avstemming115 ikke er med, så setter vi den til i går og krysser fingrene
                    ?: LocalDateTime.now().with(LocalTime.of(10, 10, 0, 0)).minusDays(1) 
            }
            val keep = avstemmingdag == null || (avstemmingdag in avstemFom..avstemTom)
            if (!keep) appLog.warn("Filter oppdrag not suited for todays avstemming k:${dao.key} p:${dao.partition} o:${dao.offset}")
            keep
        }.also {
            appLog.info("Keeping ${it.size} of the fetched ones")
        }.forEach { dao ->
            if (dao.value == null) {
                appLog.error("Found tombstone key:${dao.key} p:${dao.partition} o:${dao.offset}. Tombstones are not necessary on topics with retention.")
            } else {
                oppdragDaos.accAndDedup(dao)
            }
        }

        oppdragDaos.reduce()

        return oppdragDaos.values
            .filterNot { it.isEmpty() }
            .groupBy { requireNotNull(it.first().oppdrag).oppdrag110.kodeFagomraade.trimEnd() }
            .map { (fagområde, daos) ->
                appLog.debug("create oppdragsdatas for $fagområde")
                daos.flatten().forEach { 
                    appLog.debug("oppdragsdata k:${it.key} p:${it.partition} o:${it.offset}")
                }

                val avstemmingId = AvstemmingFactory.genererId()

                val oppdragsdatas = daos.flatten().mapNotNull { it.oppdrag }.map { oppdrag ->
                    Oppdragsdata(
                        fagsystem = Fagsystem.fromFagområde(fagområde),
                        personident = Personident(oppdrag.oppdrag110.oppdragGjelderId.trimEnd()),
                        sakId = SakId(oppdrag.oppdrag110.fagsystemId.trimEnd()),
                        lastDelytelseId = oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId.trimEnd(),
                        innsendt = oppdrag.oppdrag110?.avstemming115?.tidspktMelding?.trimEnd()?.toLocalDateTime() ?: LocalDateTime.now().with(LocalTime.of(10, 10, 0, 0)).minusDays(1) ,
                        totalBeløpAllePerioder = oppdrag.oppdrag110.oppdragsLinje150s.sumOf {
                            it.sats.toLong().toUInt()
                        },
                        kvittering = oppdrag.mmel?.let { mmel ->
                            Kvittering(
                                alvorlighetsgrad = mmel.alvorlighetsgrad.trimEnd(),
                                kode = mmel.kodeMelding?.trimEnd(),
                                melding = mmel.beskrMelding?.trimEnd(),
                            )
                        },
                    )
                }
                val avstemming = Avstemming(avstemmingId, avstemFom, avstemTom, oppdragsdatas)
                val messages = AvstemmingFactory.create(avstemming)
                // FIXME: hvis forrige iter i forEach gikk bra, men neste feiler. Så har vi allerede sendt ut disse
                // Hvordan kan vi gjøre alle forEach (fagområde, daos) atomisk? 
                fagområde to messages
            }
    }
}

private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
private fun String.toLocalDate(): LocalDate = LocalDate.parse(this, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
private fun String.toLocalDateTime(): LocalDateTime =
    LocalDateTime.parse(this, DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS"))

private fun MutableMap<String, Set<Dao>>.accAndDedup(dao: Dao) {
    val daosForKey = getOrDefault(dao.key, emptySet())
    if (daosForKey.none { it.value == dao.value }) {
        this[dao.key] = daosForKey + dao
    }
}

/**
 * Alle oppdrag skal få en kvittering og de som har kvittering har presedens over de som er ukvitterte
 */
private fun MutableMap<String, Set<Dao>>.reduce() {
    val obsoleteDaos = mutableListOf<Dao>()
    entries.forEach { (_, daos) ->
        daos.filter { dao ->
            dao.oppdrag?.mmel == null
        }.forEach { dao ->
            if (daos.findCorrelated(dao) != null) {
                obsoleteDaos.add(dao)
            } else {
                appLog.warn("Found oppdrag uten kvittering key:${dao.key} p:${dao.partition} o:${dao.offset}")
            }
        }
    }
    obsoleteDaos.forEach { dao ->
        appLog.debug("Found oppdrag with kvittering, removing ukvittert key:${dao.key} p:${dao.partition} o:${dao.offset}")
        this[dao.key] = this[dao.key]!! - dao
    }
}

private fun Set<Dao>.findCorrelated(dao: Dao): Dao? {
    val firstDelytelseId = dao.oppdrag?.oppdrag110?.oppdragsLinje150s?.first()?.delytelseId?.trimEnd()
    val lastDelytelseId = dao.oppdrag?.oppdrag110?.oppdragsLinje150s?.last()?.delytelseId?.trimEnd()
    val mmel = dao.oppdrag?.mmel

    return this.singleOrNull {
        firstDelytelseId == it.oppdrag?.oppdrag110?.oppdragsLinje150s?.first()?.delytelseId?.trimEnd() &&
            lastDelytelseId == it.oppdrag?.oppdrag110?.oppdragsLinje150s?.last()?.delytelseId?.trimEnd() &&
            mmel != it.oppdrag?.mmel
    }
}
