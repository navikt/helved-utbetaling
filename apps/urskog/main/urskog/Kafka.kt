package urskog

import libs.kafka.*
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import urskog.models.*
import urskog.models.Utbetaling

object Topics {
    private inline fun <reified V: Any> xml() = Serdes(StringSerde, XmlSerde.serde<V>())
    private inline fun <reified V: Any> json() = Serdes(StringSerde, JsonSerde.jackson<V>())

    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val kvittering = Topic("helved.kvittering.v1", xml<Oppdrag>())
    val utbetalinger = Topic("helved.utbetalinger.v1", Serdes(StringSerde, JsonSerde.jackson<Utbetaling>()))
    val status = Topic("helved.status.v1", json<StatusReply>())
}

object StateStores {
    val keystore: StateStoreName = "fk-uid-store"
}

fun createTopology(oppdragProducer: OppdragMQProducer): Topology = topology {
    val oppdrag = consume(Topics.oppdrag)

    oppdrag
        .map { _ -> StatusReply(status = Status.HOS_OPPDRAG) }
        .produce(Topics.status)

    oppdrag.forEach { _, xml ->
        oppdragProducer.send(xml)
    }

    consume(Topics.utbetalinger)
        .map { u -> u }
        .rekey(JsonSerde.jackson()) { utbetaling -> OppdragForeignKey.from(utbetaling) }
        .map(JsonSerde.jackson()) { utbetaling -> utbetaling.uid }
        .materialize(StateStores.keystore)

    buildList<String> {
        add("")
    }
    consume(Topics.kvittering)
        .map { kvitt ->
            if (kvitt.mmel == null) {
                StatusReply(Status.OK)
            } else {
                when (kvitt.mmel.alvorlighetsgrad) {
                    "00" -> StatusReply(Status.OK)
                    "04" -> StatusReply(Status.FEILET, ApiError(400, kvitt.mmel.beskrMelding, null, null))
                    "08" -> StatusReply(Status.FEILET, ApiError(400, kvitt.mmel.beskrMelding, null, null))
                    "12" -> StatusReply(Status.FEILET, ApiError(500, kvitt.mmel.beskrMelding, null, null))
                    else -> StatusReply(Status.FEILET, ApiError(500, "umulig feil, skal aldri forekomme. Hvis du ser denne er alt håp ute.", null, null))
                }
            }
        }
        .produce(Topics.status)
}

