package urskog

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import kotlinx.coroutines.runBlocking
import libs.kafka.*
import libs.kafka.processor.DedupProcessor
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import kotlin.time.Duration.Companion.hours

object Topics {
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val simuleringer = Topic("helved.simuleringer.v1", jaxb<SimulerBeregningRequest>())
    val dryrunAap = Topic("helved.dryrun-aap.v1", json<Simulering>())
    val dryrunDp = Topic("helved.dryrun-dp.v1", json<Simulering>())
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
                    map { (sim, _) -> sim }
                        .map { Result.catch { into(it) } }
                        .branch({ it.isOk() }) { map { it.unwrap() }.produce(Topics.dryrunAap) }
                        .default { map { it.unwrapErr() }.produce(Topics.status) }
                }
                .branch({ (_, fagsystem) -> fagsystem == Fagsystem.DAGPENGER }) {
                    map { (sim, _) -> sim }
                        .map { Result.catch { into(it) } }
                        .branch({ it.isOk() }) { map { it.unwrap() }.produce(Topics.dryrunDp) }
                        .default { map { it.unwrapErr() }.produce(Topics.status) }
                }
                .branch({ (_, fagsystem) -> fagsystem == Fagsystem.TILLEGGSSTØNADER }) {
                    map { (sim, _) -> sim }
                        .map { Result.catch { intoV1(it) } }
                        .branch({ it.isOk() }) { map { it.unwrap() }.produce(Topics.dryrunTilleggsstønader) }
                        .default { map { it.unwrapErr() }.produce(Topics.status) }
                }
                .branch({ (_, fagsystem) -> fagsystem == Fagsystem.TILTAKSPENGER }) {
                    map { (sim, _) -> sim }
                        .map { Result.catch { intoV1(it) } }
                        .branch({ it.isOk() }) { map { it.unwrap() }.produce(Topics.dryrunTiltakspenger) }
                        .default { map { it.unwrapErr() }.produce(Topics.status) }
                }
        }
        .default {
            map { result -> result.unwrapErr() }.produce(Topics.status)
        }
}

private val mapper: libs.xml.XMLMapper<Oppdrag> = libs.xml.XMLMapper()

fun dedupHash(oppdrag: Oppdrag): Int {
    // avstemmingstidspunkt blir ulikt mellom 2 oppdrag på rappen
    // oppdrag.oppdrag110.avstemming115 = null
    return mapper.writeValueAsString(oppdrag).hashCode()
}

fun Topology.oppdrag(oppdragProducer: OppdragMQProducer, meters: MeterRegistry) {
    val kvitteringKTable = consume(Tables.kvittering)
    val oppdragTopic = consume(Topics.oppdrag)

    val kstore = oppdragTopic
        .filter { oppdrag -> oppdrag.mmel == null }
        .mapKeyAndValue { uid, xml -> OppdragForeignKey.from(xml) to uid }
        .materialize(Stores.keystore)

    kstore.join(kvitteringKTable)
        .filter { (uid, kvitt) -> kvitt?.mmel != null && uid != null }
        .mapKeyAndValue { _, (uid, kvitt) -> uid!! to kvitt!! }
        .produce(Topics.oppdrag)

    oppdragTopic
        .branch({ o -> o.mmel == null }) {
            filter { o -> o.mmel == null }
                .processor(
                    DedupProcessor.supplier(
                        Topics.oppdrag.serdes,
                        1.hours,
                        "dedup-oppdrag-mq",
                        ::dedupHash,
                    ){ xml -> 
                        oppdragProducer.send(xml) 
                    }
                )
                .map { xml -> StatusReply.sendt(xml) }
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
    return when (o.mmel) {
        null -> StatusReply(Status.OK) // TODO: denne kan skape feil hvis statusReply blir kalt fra et sted som ikke har kvittering
        else -> when (o.mmel.alvorlighetsgrad) {
            "00" -> StatusReply.ok(o)
            "04" -> StatusReply.ok(o, ApiError(200, o.mmel.beskrMelding))
            "08" -> StatusReply.err(o, ApiError(400, o.mmel.beskrMelding))
            "12" -> StatusReply.err(o, ApiError(500, o.mmel.beskrMelding))
            else -> StatusReply.err(o, ApiError(500, "umulig feil, skal aldri forekomme. Hvis du ser denne er alt håp ute."))
        }
    }
}

