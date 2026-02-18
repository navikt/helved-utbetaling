package vedskiva

import libs.kafka.*
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import libs.jdbc.concurrency.transaction
import libs.jdbc.Jdbc

private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
private fun String.toLocalDateTime() = LocalDateTime.parse(this, formatter)
private val mapper = libs.xml.XMLMapper<Oppdrag>()

object Topics {
    val avstemming = Topic("helved.avstemming.v1", xml<Avstemmingsdata>())
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
}

open class Kafka : KafkaFactory

fun topology() = topology {
    consume(Topics.oppdrag).forEach { _, oppdrag ->
        val avstemming = oppdrag.oppdrag110.avstemming115 
        val hashKey = mapper
            .copy(oppdrag)
            .apply { mmel = null }
            .let { mapper.writeValueAsString(it).hashCode() }
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

        runBlocking(Jdbc.context) {
            transaction {
                dao.insert()
            }
        }
    }
}

