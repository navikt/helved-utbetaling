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
    val oppdragsdata = Topic("helved.oppdragsdata.v1", json<Oppdragsdata>())
    val avstemming = Topic("helved.avstemming.v1", xml<Avstemmingsdata>())
}

object Stores {
    val keystore = Store<OppdragForeignKey, UtbetalingId>("fk-uid-store", Serdes(JsonSerde.jackson(), JsonSerde.jackson()))
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
        .mapKeyAndValue { uid, xml -> OppdragForeignKey.from(xml) to UtbetalingId(UUID.fromString(uid)) }
        .materialize(Stores.keystore)

    kstore.join(kvitteringKTable)
        .filter { (_, kvitt) -> kvitt?.mmel != null }
        .mapKeyAndValue { _, (uid, kvitt) -> uid.id.toString() to kvitt!! }
        .produce(Topics.oppdrag)

    oppdragTopic
        .branch({ o -> o.mmel == null }) {
            filter { o -> o.mmel == null }
                .map { xml -> oppdragProducer.send(xml) }
                .map { _ -> StatusReply(status = Status.HOS_OPPDRAG) }
                .produce(Topics.status)
        }
        .branch( { o -> o.mmel != null}) {
            filter { o -> o.mmel != null }.map { kvitt ->
                val fagsystem = Fagsystem.fromFagområde(kvitt.oppdrag110.kodeFagomraade.trimEnd())
                val statusReply = kvitt.mmel.into()
                    meters.counter("kvitteringer", listOf(
                        Tag.of("status", statusReply.status.name),
                        Tag.of("fagsystem", fagsystem.name),
                    )).increment()
                statusReply
            }
            .produce(Topics.status)
        }

    oppdragTopic.map { o ->
        Oppdragsdata(
            fagsystem = Fagsystem.fromFagområde(o.oppdrag110.kodeFagomraade.trimEnd()),
            personident = Personident(o.oppdrag110.oppdragGjelderId.trimEnd()),
            sakId = SakId(o.oppdrag110.fagsystemId.trimEnd()),
            lastDelytelseId = o.oppdrag110.oppdragsLinje150s.last().delytelseId.trimEnd(),
            avstemmingsdag = LocalDateTime.parse(o.oppdrag110.avstemming115.tidspktMelding.trimEnd(), formatter).toLocalDate(),
            totalBeløpAllePerioder = o.oppdrag110.oppdragsLinje150s.sumOf { it.sats.toLong().toUInt() },
            kvittering = if (o.mmel == null) null else Kvittering(
                kode = o.mmel.kodeMelding?.trimEnd(), // disse finnes bare ved varsel og avvist
                alvorlighetsgrad = o.mmel.alvorlighetsgrad.trimEnd(),
                melding = o.mmel.beskrMelding?.trimEnd(), // disse finnes bare ved varsel og avvist
            )
        )
    }
    .produce(Topics.oppdragsdata)
}

fun Topology.avstemming(avstemProducer: AvstemmingMQProducer) {
    consume(Topics.avstemming).forEach { _, v ->
        avstemProducer.send(v)
    }
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


