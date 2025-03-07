package urskog

import java.util.UUID
import libs.kafka.*
import models.*
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningResponse
import kotlinx.coroutines.runBlocking

object Topics {
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val simulering = Topic("helved.simulering.v1", jaxb<SimulerBeregningRequest,no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.ObjectFactory>())
    val simuleringResult = Topic("helved.simulering-result.v1", jaxb<SimulerBeregningResponse,no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.ObjectFactory>())
    val kvittering = Topic("helved.kvittering.v1", xml<Oppdrag>())
    val status = Topic("helved.status.v1", json<StatusReply>())
    val kvitteringQueue = Topic<OppdragForeignKey, Oppdrag>("helved.kvittering-queue.v1", Serdes(JsonSerde.jackson(), XmlSerde.serde()))
}

object Stores {
    val keystore = Store<OppdragForeignKey, UtbetalingId>("fk-uid-store", Serdes(JsonSerde.jackson(), JsonSerde.jackson()))
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

    consume(Topics.simulering)
        .map { sim -> runBlocking { simuleringService.simuler(sim) }}
        .produce(Topics.simuleringResult)

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
                    else -> StatusReply(Status.FEILET, ApiError(500, "umulig feil, skal aldri forekomme. Hvis du ser denne er alt håp ute."))
                }
            }
        }
        .produce(Topics.status)
}

