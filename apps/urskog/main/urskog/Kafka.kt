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

object Topics {
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val simuleringer = Topic("helved.simuleringer.v1", jaxb<SimulerBeregningRequest>())
    val dryrunAap = Topic("helved.dryrun-aap.v1", json<Simulering>())
    val dryrunTilleggsstønader = Topic("helved.dryrun-ts.v1", json<models.v1.Simulering>())
    val dryrunTiltakspenger = Topic("helved.dryrun-tp.v1", json<models.v1.Simulering>())
    val kvittering = Topic("helved.kvittering.v1", xml<Oppdrag>())
    val status = Topic("helved.status.v1", json<StatusReply>())
    val kvitteringQueue = Topic<OppdragForeignKey, Oppdrag>("helved.kvittering-queue.v1", Serdes(JsonSerde.jackson(), XmlSerde.xml()))
    val avstemming = Topic("helved.avstemming.v1", json<Oppdragsdata>())
}

object Stores {
    val keystore = Store<OppdragForeignKey, UtbetalingId>("fk-uid-store", Serdes(JsonSerde.jackson(), JsonSerde.jackson()))
}

object Tables {
    val kvitteringQueue = Table(Topics.kvitteringQueue)
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

fun Topology.oppdrag(oppdragProducer: OppdragMQProducer) {
    val kvitteringQueueKTable = consume(Tables.kvitteringQueue)
    val oppdrag = consume(Topics.oppdrag)

    val kstore = oppdrag
        .mapKeyAndValue { uid, xml -> OppdragForeignKey.from(xml) to UtbetalingId(UUID.fromString(uid)) }
        .materialize(Stores.keystore)

    oppdrag
        .map { xml -> oppdragProducer.send(xml) }
        .map { _ -> StatusReply(status = Status.HOS_OPPDRAG) }
        .produce(Topics.status)

    kstore.join(kvitteringQueueKTable)
        .filter { (_, kvitt) -> kvitt != null }
        .mapKeyAndValue { _, (uid, kvitt) -> uid.id.toString() to kvitt!! }
        .produce(Topics.kvittering)
}

fun Topology.kvittering(meters: MeterRegistry) {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")

    val kvitteringer = consume(Topics.kvittering)

    kvitteringer
        .map { kvitt ->
            val fagsystem = Fagsystem.fromFagområde(kvitt.oppdrag110.kodeFagomraade.trimEnd())
            val statusReply = kvitt.mmel.into()
                meters.counter("kvitteringer", listOf(
                    Tag.of("status", statusReply.status.name),
                    Tag.of("fagsystem", fagsystem.name),
                )).increment()
            statusReply
        }
        .produce(Topics.status)

    kvitteringer
        .map { kvitt ->
            Oppdragsdata(
                fagsystem = Fagsystem.fromFagområde(kvitt.oppdrag110.kodeFagomraade.trimEnd()),
                status = kvitt.mmel.into(),
                personident = Personident(kvitt.oppdrag110.oppdragGjelderId.trimEnd()),
                sakId = SakId(kvitt.oppdrag110.fagsystemId.trimEnd()),
                avstemmingsdag = LocalDateTime.parse(kvitt.oppdrag110.avstemming115.nokkelAvstemming.trimEnd(), formatter).toLocalDate(),
                totalBeløpAllePerioder = kvitt.oppdrag110.oppdragsLinje150s.sumOf { it.sats.toLong().toUInt() },
                kvittering = Kvittering(
                    kode = kvitt.mmel.kodeMelding.trimEnd(), // todo finnes disse til en hver tid?
                    alvorlighetsgrad = kvitt.mmel.alvorlighetsgrad.trimEnd(),
                    melding = kvitt.mmel.beskrMelding.trimEnd(), // todo finnes disse til en hver tid?
                )
            )
        }
        .produce(Topics.avstemming)
}

private fun Mmel?.into(): StatusReply = when (this) {
    null -> StatusReply(Status.OK)
    else -> when (this.alvorlighetsgrad) {
        "00" -> StatusReply(Status.OK)
        "04" -> StatusReply(Status.OK, ApiError(200, this.beskrMelding))
        "08" -> StatusReply(Status.FEILET, ApiError(400, this.beskrMelding))
        "12" -> StatusReply(Status.FEILET, ApiError(500, this.beskrMelding))
        else -> StatusReply(Status.FEILET, ApiError(500, "umulig feil, skal aldri forekomme. Hvis du ser denne er alt håp ute."))
    }
}


