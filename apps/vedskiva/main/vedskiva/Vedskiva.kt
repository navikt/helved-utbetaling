package vedskiva

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.postgres.Jdbc
import libs.postgres.Migrator
import libs.postgres.concurrency.transaction
import libs.utils.logger
import libs.utils.secureLog
import libs.kafka.KafkaFactory
import models.*
import io.ktor.client.HttpClient
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import no.trygdeetaten.skjema.oppdrag.TkodeStatusLinje

val appLog = logger("app")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}, se secureLog")
        secureLog.error("Uhåndtert feil ${e.javaClass.canonicalName}", e)
    }

    database()
    vedskiva()
}

fun database(config: Config = Config()) {
    Jdbc.initialize(config.jdbc)
    runBlocking {
        withContext(Jdbc.context) {
            Migrator(config.jdbc.migrations).migrate()
        }
    }
}

fun vedskiva(
    config: Config = Config(),
    kafka: KafkaFactory = Kafka(),
) {
    val peisschtappern = PeisschtappernClient(config)
    val avstemmingProducer = kafka.createProducer(config.kafka, Topics.avstemming)

    runBlocking {
        withContext(Jdbc.context) {
            if (!LocalDate.now().erHelligdag()) {
               val today = LocalDate.now()
               val last: Scheduled? = transaction {
                   Scheduled.lastOrNull()
               } 
               appLog.info("last scheduled: $last")
               if (today == last?.created_at) {
                   appLog.info("Already avstemt today")
                   return@withContext
               }

               val oppdragDaos = mutableMapOf<String, Set<Dao>>()
               val avstemFom = (last?.avstemt_tom?.plusDays(1) ?: today.forrigeVirkedag()).atStartOfDay() 
               val avstemTom = today.atStartOfDay().minusNanos(1)

               peisschtappern.oppdrag(
                   fom = avstemFom, 
                   tom = avstemTom,
               ).filter { dao -> 
                   val avstemmingdag = dao.oppdrag?.let { oppdrag -> 
                       val tidspktMelding = oppdrag.oppdrag110.avstemming115.tidspktMelding.trimEnd() 
                       LocalDateTime.parse(tidspktMelding, formatter).toLocalDate() 
                   } 
                   val keep = avstemmingdag in listOf(today, null)
                   if (!keep) appLog.error("fant oppdrag som ikke skulle avstemmes i dag, key:${dao.key} p:${dao.partition} o:${dao.offset}")
                   keep
               }.forEach { dao ->
                   when (dao.value) {
                       null -> oppdragDaos.remove(dao.key)
                       else -> {
                           oppdragDaos.reduce(dao)
                           oppdragDaos.deduplicate(dao)
                       }
                   }
               } 
               val fom = last?.avstemt_tom?.plusDays(1) ?: LocalDate.now().forrigeVirkedag()
               val tom = today.minusDays(1)

               oppdragDaos.values
                   .filterNot { it.isEmpty() } 
                   .groupBy { requireNotNull(it.first().oppdrag).oppdrag110.kodeFagomraade.trimEnd() }
                   .forEach { (fagområde, daos) -> 
                       val oppdragsdatas = daos.flatten().mapNotNull { it.oppdrag }.map { oppdrag ->
                           Oppdragsdata(
                            fagsystem = Fagsystem.fromFagområde(fagområde),
                            personident = Personident(oppdrag.oppdrag110.oppdragGjelderId.trimEnd()),
                            sakId = SakId(oppdrag.oppdrag110.fagsystemId.trimEnd()),
                            lastDelytelseId = oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId.trimEnd(),
                            avstemmingsdag = LocalDateTime.parse(oppdrag.oppdrag110.avstemming115.tidspktMelding.trimEnd(), formatter).toLocalDate(),
                            innsendt = oppdrag.oppdrag110.oppdragsLinje150s.first().vedtakId.trimEnd().toLocalDate(),
                            totalBeløpAllePerioder = oppdrag.oppdrag110.oppdragsLinje150s.sumOf {it.sats.toLong().toUInt() },
                            kvittering = oppdrag.mmel?.let { mmel ->
                                Kvittering(
                                    alvorlighetsgrad = mmel.alvorlighetsgrad.trimEnd(),
                                    kode = mmel.kodeMelding?.trimEnd(),
                                    melding = mmel.beskrMelding?.trimEnd(),
                                )
                            },
                           )
                       }
                       val avstemming = Avstemming(fom, tom, oppdragsdatas)
                       val messages = AvstemmingService.create(avstemming)
                       val id = messages.first().aksjon.avleverendeAvstemmingId
                       messages.forEach { msg -> avstemmingProducer.send(id, msg, 0) }
                       appLog.info("Fullført grensesnittavstemming for $fagområde id: $id")
                   }

               transaction {
                   Scheduled(LocalDate.now(), avstemFom.toLocalDate(), avstemTom.toLocalDate()).insert()
               }
            } else {
                appLog.info("Avstemmer ikke i helg eller på helligdag")
            }
        }
    }
}

private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
private fun String.toLocalDate(): LocalDate = LocalDate.parse(this, DateTimeFormatter.ofPattern("yyyy-MM-dd"))

/**
 * Alle oppdrag skal få en kvittering og de som har kvittering har presedens over de som er ukvitterte
 */
private fun MutableMap<String, Set<Dao>>.reduce(dao: Dao) {
    val daosForKey = getOrDefault(dao.key, emptySet())
    val associatedDaoWithoutKvittering = daosForKey.find { d -> 
        val currentLastId = d.oppdrag?.oppdrag110?.oppdragsLinje150s?.last()?.delytelseId?.trimEnd()
        val nextLastId = dao.oppdrag?.oppdrag110?.oppdragsLinje150s?.first()?.delytelseId?.trimEnd()
        val currentFirstId = d.oppdrag?.oppdrag110?.oppdragsLinje150s?.last()?.delytelseId?.trimEnd()
        val nextFirstId = dao.oppdrag?.oppdrag110?.oppdragsLinje150s?.first()?.delytelseId?.trimEnd()
        currentLastId == nextLastId && currentFirstId == nextFirstId && d.oppdrag?.mmel == null
    }
    if (associatedDaoWithoutKvittering != null) {
        this[dao.key] = daosForKey - associatedDaoWithoutKvittering
    }
}

/**
 * For å være idempotent må vi skrelle vekk duplikater
 */
private fun MutableMap<String, Set<Dao>>.deduplicate(dao: Dao) {
    val daosForKey = getOrDefault(dao.key, emptySet())

    if (daosForKey.none { it.value == dao.value }) {
        this[dao.key] = daosForKey + dao
    } else {
        appLog.warn("Found duplicate key:${dao.key} topic:${dao.topic_name} partition:${dao.partition}")
    }
}

