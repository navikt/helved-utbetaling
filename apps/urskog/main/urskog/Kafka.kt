package urskog

import java.util.UUID
import libs.kafka.*
import models.*
import no.trygdeetaten.skjema.oppdrag.Oppdrag

object Topics {
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val kvittering = Topic("helved.kvittering.v1", xml<Oppdrag>())
    // val utbetalinger = Topic("helved.utbetalinger.v1", json<Utbetaling>())
    val status = Topic("helved.status.v1", json<StatusReply>())
}

object Stores {
    val keystore = Store<OppdragForeignKey, UtbetalingId>("fk-uid-store", Serdes(JsonSerde.jackson(), JsonSerde.jackson()))
}

fun createTopology(oppdragProducer: OppdragMQProducer): Topology = topology {
    val oppdrag = consume(Topics.oppdrag)

    oppdrag
        .map { _ -> StatusReply(status = Status.HOS_OPPDRAG) }
        .produce(Topics.status)

    oppdrag
        .map { uid, xml -> uid to xml }
        .rekey { (_, xml) -> OppdragForeignKey.from(xml) }
        .secureLogWithKey { key, _ -> info("saving fk: $key")}
        .map { (uid, xml) -> 
            oppdragProducer.send(xml)
            UtbetalingId(UUID.fromString(uid))
        }
        .materialize(Stores.keystore)

    // consume(Topics.utbetalinger)
    //     .map { u -> u }
    //     .rekey { utbetaling -> OppdragForeignKey.from(utbetaling) }
    //     .map { utbetaling -> utbetaling.uid }
    //     .materialize(Stores.keystore)

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

