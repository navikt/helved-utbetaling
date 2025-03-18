package urskog

import kotlinx.coroutines.runBlocking
import libs.kafka.*
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
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
}

object Stores {
    val keystore =
        Store<OppdragForeignKey, UtbetalingId>("fk-uid-store", Serdes(JsonSerde.jackson(), JsonSerde.jackson()))
}

object Tables {
    val kvitteringQueue = Table(Topics.kvitteringQueue)
}

fun createTopology(
    oppdragProducer: OppdragMQProducer,
    simuleringService: SimuleringService,
): Topology = topology {
    val oppdrag = consume(Topics.oppdrag)
    val kvitteringQueue = consume(Tables.kvitteringQueue)

    oppdrag
        .map { xml -> oppdragProducer.send(xml) }
        .map { _ -> StatusReply(status = Status.HOS_OPPDRAG) }
        .produce(Topics.status)

    consume(Topics.simuleringer)
        .map { sim ->
            Result.catch {
                runBlocking {
                    simuleringService.simuler(sim)
                }
            }
        }
        .branch({ it.isOk() }) {
            map { it -> it.unwrap() }
                .branch({ it.response.simulering.beregningsPeriodes.first().beregningStoppnivaas.first().kodeFagomraade == "AAP" }) {
                    map(::into).produce(Topics.dryrunAap)
                }
                .branch({ it.response.simulering.beregningsPeriodes.first().beregningStoppnivaas.first().kodeFagomraade == "TILLEGGSSTØNADER" }) {
                    // TILLEGGSSTØNADER,
                    // TILLEGGSSTØNADER_ARENA,
                    // TILLEGGSSTØNADER_ARENA_MANUELL_POSTERING,
                    map(::intoV1).produce(Topics.dryrunTilleggsstønader)
                }
                .branch({ it.response.simulering.beregningsPeriodes.first().beregningStoppnivaas.first().kodeFagomraade == "TILTAKSPENGER" }) {
                    // TILTAKSPENGER,
                    // TILTAKSPENGER_ARENA,
                    // TILTAKSPENGER_ARENA_MANUELL_POSTERING,
                    map(::intoV1).produce(Topics.dryrunTiltakspenger)
                }
        }
        .default {
            map { it -> it.unwrapErr() }.produce(Topics.status)
        }

    val kstore = oppdrag
        .mapKeyAndValue { uid, xml -> OppdragForeignKey.from(xml) to UtbetalingId(UUID.fromString(uid)) }
        .materialize(Stores.keystore)

    kstore.join(kvitteringQueue)
        .filter { (_, kvitt) -> kvitt != null }
        .mapKeyAndValue { _, (uid, kvitt) -> uid.id.toString() to kvitt!! }
        .produce(Topics.kvittering)

    consume(Topics.kvittering)
        .map { kvitt ->
            if (kvitt.mmel == null) {
                StatusReply(Status.OK)
            } else {
                when (kvitt.mmel.alvorlighetsgrad) {
                    "00" -> StatusReply(Status.OK)
                    "04" -> StatusReply(Status.FEILET, ApiError(400, kvitt.mmel.beskrMelding))
                    "08" -> StatusReply(Status.FEILET, ApiError(400, kvitt.mmel.beskrMelding))
                    "12" -> StatusReply(Status.FEILET, ApiError(500, kvitt.mmel.beskrMelding))
                    else -> StatusReply(
                        Status.FEILET,
                        ApiError(500, "umulig feil, skal aldri forekomme. Hvis du ser denne er alt håp ute.")
                    )
                }
            }
        }
        .produce(Topics.status)
}



