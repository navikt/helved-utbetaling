package vedskiva

import libs.kafka.*
import libs.utils.sha256
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.concurrency.transaction

private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
private fun String.toLocalDateTime() = LocalDateTime.parse(this, formatter)
private val mapper = libs.xml.XMLMapper<Oppdrag>()

object Topics {
    val avstemming = Topic("helved.avstemming.v1", xml<Avstemmingsdata>())
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
}

open class Kafka : KafkaFactory

fun topology(jdbcCtx: CoroutineDatasource) = topology {
    consume(Topics.oppdrag)
        // ved avvent feilregistrering vi har ikke avstemming115
        .filter { oppdrag -> oppdrag.oppdrag110.avstemming115 != null }
        .forEach { _, oppdrag ->
            val avstemming = oppdrag.oppdrag110.avstemming115 
            val hashKey = mapper
                .copy(oppdrag)
                .apply { mmel = null }
                .let { mapper.writeValueAsString(it).sha256() }
            val dao = OppdragDao(
                nokkelAvstemming = avstemming.nokkelAvstemming.trimEnd().toLocalDateTime(),
                hashKey = hashKey,
                kodeFagomraade = oppdrag.oppdrag110.kodeFagomraade.trimEnd(),
                personident = oppdrag.oppdrag110.oppdragGjelderId.trimEnd(),
                fagsystemId = oppdrag.oppdrag110.fagsystemId.trimEnd(),
                lastDelytelseId = oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId.trimEnd(),
                tidspktMelding = avstemming.tidspktMelding.trimEnd().toLocalDateTime(),
                sats = oppdrag.oppdrag110.oppdragsLinje150s.sumOf { it.sats.toLong() },
                createdAt = LocalDateTime.now(),
                alvorlighetsgrad = oppdrag.mmel?.alvorlighetsgrad,
                kodeMelding = oppdrag.mmel?.kodeMelding,
                beskrMelding = oppdrag.mmel?.beskrMelding,
            )

            runBlocking(jdbcCtx) {
                transaction {
                    dao.insert()
                }
            }
    }
}

