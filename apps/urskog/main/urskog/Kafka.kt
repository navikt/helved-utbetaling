package urskog

import kotlinx.coroutines.runBlocking
import libs.kafka.*
import libs.kafka.processor.*
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import no.trygdeetaten.skjema.oppdrag.Mmel
import java.util.*
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.state.ValueAndTimestamp
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object Topics {
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val simuleringer = Topic("helved.simuleringer.v1", jaxb<SimulerBeregningRequest>())
    val dryrunAap = Topic("helved.dryrun-aap.v1", json<Simulering>())
    val dryrunTilleggsstønader = Topic("helved.dryrun-ts.v1", json<models.v1.Simulering>())
    val dryrunTiltakspenger = Topic("helved.dryrun-tp.v1", json<models.v1.Simulering>())
    val kvittering = Topic("helved.kvittering.v1", xml<Oppdrag>())
    val status = Topic("helved.status.v1", json<StatusReply>())
    val kvitteringQueue = Topic<OppdragForeignKey, Oppdrag>("helved.kvittering-queue.v1", Serdes(JsonSerde.jackson(), XmlSerde.xml()))
}

object Stores {
    val keystore =
        Store<OppdragForeignKey, UtbetalingId>("fk-uid-store", Serdes(JsonSerde.jackson(), JsonSerde.jackson()))
}

object Tables {
    val kvitteringQueue = Table(Topics.kvitteringQueue)
}

class AvstemmingScheduler(
    ktable: KTable<OppdragForeignKey, Oppdrag>,
    private val mq: AvstemmingMQProducer,
): StateScheduleProcessor<OppdragForeignKey, Oppdrag>(
    "avstemming-${ktable.table.stateStoreName}-scheduler",
    table = ktable,
    interval = 1.toDuration(DurationUnit.HOURS)
) {
    override fun schedule(wallClockTime: Long, store: StateStore<OppdragForeignKey, ValueAndTimestamp<Oppdrag>>) {
        // TODO: is it only possible to grensesnittavstemme once per day?
        if (true) return 

        store.filter(limit = 10_000) {
            val avstemmingTidspunkt = LocalDateTime.parse(it.value.value().oppdrag110.avstemming115.nokkelAvstemming)
            avstemmingTidspunkt.isAfter(LocalDate.now().atStartOfDay()) && avstemmingTidspunkt.isBefore(LocalDate.now().nesteVirkedag().atStartOfDay())
        }.groupBy {
            it.value().oppdrag110.kodeFagomraade.trimEnd()
        }.mapValues { (_, oppdrags) ->
            oppdrags.map {
                val oppdrag = it.value().oppdrag110
                val mmel = it.value().mmel
                Oppdragsdata(
                    status = mmel.into(), 
                    personident = Personident(oppdrag.oppdragGjelderId.trimEnd()),
                    sakId = SakId(oppdrag.fagsystemId.trimEnd()),
                    avstemmingtidspunkt = LocalDateTime.parse(oppdrag.avstemming115.nokkelAvstemming.trimEnd(), DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")),
                    totalBeløpAllePerioder = oppdrag.oppdragsLinje150s.sumOf { it.sats.toLong().toUInt() },
                    kvittering = Kvittering(
                        kode = mmel.kodeMelding.trimEnd(),
                        alvorlighetsgrad = mmel.alvorlighetsgrad.trimEnd(),
                        melding = mmel.beskrMelding.trimEnd(),
                    )
                )
            }
        }.forEach { (fagsystem, oppdragsdata) ->
            val avstemming = Avstemming(
                fagsystem = Fagsystem.fromFagområde(fagsystem),
                fom = LocalDate.now().atStartOfDay(), // TODO: forrige virkedag
                tom = LocalDate.now().nesteVirkedag().atStartOfDay(),
                oppdragsdata = oppdragsdata,
            )
            val messages = AvstemmingService.create(avstemming)
            val avstemmingId = messages.first().aksjon.avleverendeAvstemmingId
            messages.forEach { message -> mq.send(message) }
            appLog.info("Fullført grensesnittavstemming for id: $avstemmingId")
        }

    }
}

fun createTopology(
    oppdragProducer: OppdragMQProducer,
    avstemProducer: AvstemmingMQProducer,
    simuleringService: SimuleringService,
): Topology = topology {
    val oppdrag = consume(Topics.oppdrag)
    val kvitteringQueueKTable = consume(Tables.kvitteringQueue)
    val avstemmingScheduler = AvstemmingScheduler(kvitteringQueueKTable, avstemProducer)
    kvitteringQueueKTable.schedule(avstemmingScheduler)

    oppdrag
        .map { xml -> oppdragProducer.send(xml) }
        .map { _ -> StatusReply(status = Status.HOS_OPPDRAG) }
        .produce(Topics.status)

    consume(Topics.simuleringer)
        .map { sim ->
            Result.catch {
                runBlocking {
                    val fagsystem = Fagsystem.from(sim.request.oppdrag.kodeFagomraade) // TODO: denne må brukes videre for å finne ut hvilket topic simulering skal sendes til
                    simuleringService.simuler(sim) to fagsystem
                }
            }
        }
        .branch({ it.isOk() }) {
            map { it -> it.unwrap() }
                .branch({ (_, fagsystem) -> fagsystem == Fagsystem.AAP }) {
                    map({(sim, _) -> sim}).map(::into).produce(Topics.dryrunAap)
                }
                .branch({ (_, fagsystem) -> fagsystem == Fagsystem.TILLEGGSSTØNADER }) {
                    map({(sim, _) -> sim}).map(::intoV1).produce(Topics.dryrunTilleggsstønader)
                }
                .branch({ (_, fagsystem) -> fagsystem == Fagsystem.TILTAKSPENGER }) {
                    map({(sim, _) -> sim}).map(::intoV1).produce(Topics.dryrunTiltakspenger)
                }
        }
        .default {
            map { it -> it.unwrapErr() }.produce(Topics.status)
        }

    val kstore = oppdrag
        .mapKeyAndValue { uid, xml -> OppdragForeignKey.from(xml) to UtbetalingId(UUID.fromString(uid)) }
        .materialize(Stores.keystore)

    kstore.join(kvitteringQueueKTable)
        .filter { (_, kvitt) -> kvitt != null }
        .mapKeyAndValue { _, (uid, kvitt) -> uid.id.toString() to kvitt!! }
        .produce(Topics.kvittering)

    consume(Topics.kvittering)
        .map { kvitt -> kvitt.mmel.into() }
        .produce(Topics.status)
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


