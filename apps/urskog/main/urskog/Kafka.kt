package urskog

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import libs.kafka.*
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.trygdeetaten.skjema.oppdrag.Mmel
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import java.util.*
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata

object Topics {
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val simuleringer = Topic("helved.simuleringer.v1", jaxb<SimulerBeregningRequest>())
    val dryrunAap = Topic("helved.dryrun-aap.v1", json<Simulering>())
    val dryrunTilleggsstønader = Topic("helved.dryrun-ts.v1", json<models.v1.Simulering>())
    val dryrunTiltakspenger = Topic("helved.dryrun-tp.v1", json<models.v1.Simulering>())
    val status = Topic("helved.status.v1", json<StatusReply>())
    val kvittering = Topic<OppdragForeignKey, Oppdrag>("helved.kvittering.v1", Serdes(JsonSerde.jackson(), XmlSerde.xml()))
    val avstemming = Topic("helved.avstemming.v1", xml<Avstemmingsdata>())
}

object Stores {
    val keystore = Store<OppdragForeignKey, String>("fk-uid-store", jsonString())
}

object Tables {
    val kvittering = Table(Topics.kvittering)
}

fun Topology.simulering(simuleringService: SimuleringService) {
    consume(Topics.simuleringer)
        .map { sim ->
            Result.catch {
                runBlocking {
                    val fagsystem = Fagsystem.from(sim.request.oppdrag.kodeFagomraade)
                    simuleringService.simuler(sim) to fagsystem
                }
            }
        }
        .branch({ result -> result.isOk() }) {
            map { result -> result.unwrap() }
                .branch({ (_, fagsystem) -> fagsystem == Fagsystem.AAP }) {
                    map { (sim, _) -> sim }.map(::into).produce(Topics.dryrunAap)
                }
                .branch({ (_, fagsystem) -> fagsystem == Fagsystem.TILLEGGSSTØNADER }) {
                    map{(sim, _) -> sim}.map(::intoV1).produce(Topics.dryrunTilleggsstønader)
                }
                .branch({ (_, fagsystem) -> fagsystem == Fagsystem.TILTAKSPENGER }) {
                    map{(sim, _) -> sim}.map(::intoV1).produce(Topics.dryrunTiltakspenger)
                }
        }
        .default {
            map { result -> result.unwrapErr() }.produce(Topics.status)
        }
}

private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")

fun Topology.oppdrag(oppdragProducer: OppdragMQProducer, meters: MeterRegistry) {
    val kvitteringKTable = consume(Tables.kvittering)
    val oppdragTopic = consume(Topics.oppdrag)

    val kstore = oppdragTopic
        .filter { oppdrag -> oppdrag.mmel == null }
        .mapKeyAndValue { uid, xml -> OppdragForeignKey.from(xml) to uid }
        .materialize(Stores.keystore)

    kstore.join(kvitteringKTable)
        .filter { (_, kvitt) -> kvitt?.mmel != null }
        .mapKeyAndValue { _, (uid, kvitt) -> uid to kvitt!! }
        .produce(Topics.oppdrag)

    oppdragTopic
        .branch({ o -> o.mmel == null }) {
            filter { o -> o.mmel == null }
                .map { xml -> oppdragProducer.send(xml) }
                .map { xml -> StatusReply(status = Status.HOS_OPPDRAG, null, detaljer(xml)) }
                .produce(Topics.status)
        }
        .branch( { o -> o.mmel != null}) {
            filter { o -> o.mmel != null }.map { kvitt ->
                val statusReply = statusReply(kvitt)
                val tag_fagsystem = Tag.of("fagsystem", Fagsystem.fromFagområde(kvitt.oppdrag110.kodeFagomraade.trimEnd()).name) 
                val tag_status = Tag.of("status", statusReply.status.name) 
                meters.counter("helved_kvitteringer", listOf(tag_fagsystem, tag_status)).increment()
                meters.counter("helved_utbetalt_beløp", listOf(tag_fagsystem)).increment(kvitt.oppdrag110.oppdragsLinje150s.sumOf{ it.sats.toDouble() })
                statusReply
            }
            .produce(Topics.status)
        }
}

fun Topology.avstemming(avstemProducer: AvstemmingMQProducer) {
    consume(Topics.avstemming).forEach { _, v ->
        avstemProducer.send(v)
    }
}

private fun statusReply(o: Oppdrag): StatusReply {
    val detaljer = detaljer(o)
    return when (o.mmel) {
        null -> StatusReply(Status.OK)
        else -> when (o.mmel.alvorlighetsgrad) {
            "00" -> StatusReply(Status.OK, null, detaljer)
            "04" -> StatusReply(Status.OK, ApiError(200, o.mmel.beskrMelding), detaljer)
            "08" -> StatusReply(Status.FEILET, ApiError(400, o.mmel.beskrMelding), detaljer)
            "12" -> StatusReply(Status.FEILET, ApiError(500, o.mmel.beskrMelding), detaljer)
            else -> StatusReply(Status.FEILET, ApiError(500, "umulig feil, skal aldri forekomme. Hvis du ser denne er alt håp ute."), detaljer)
        }
    }
}

private fun detaljer(o: Oppdrag): Detaljer {
    val linjer = o.oppdrag110.oppdragsLinje150s.map { linje ->
        DetaljerLinje(
            behandlingId = linje.henvisning.trimEnd(),
            fom = linje.datoVedtakFom.toGregorianCalendar().toZonedDateTime().toLocalDate(),
            tom = linje.datoVedtakTom.toGregorianCalendar().toZonedDateTime().toLocalDate(),
            beløp = linje.sats.toLong().toUInt(),
            vedtakssats = linje.vedtakssats157?.vedtakssats?.toLong()?.toUInt(),
            klassekode = linje.kodeKlassifik.trimEnd(),
        )
    }
    return Detaljer(linjer)
}


