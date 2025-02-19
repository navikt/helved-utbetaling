package urskog

import libs.kafka.*
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import urskog.models.*

object Topics {
    val oppdrag = Topic<Oppdrag>("helved.oppdrag.v1", XmlSerde.serde())
    // val kvittering = Topic<Kvittering>("helved.kvittering.v1", XmlSerde.serde())
    val status = Topic<StatusReply>("helved.status.v1", JsonSerde.jackson())
}

object Tables {
    val status = Table(Topics.status)
}

fun createTopology(
    oppdragProducer: OppdragProducer,
): Topology = topology {
    val status = consume(Tables.status)
    val oppdrag = consume(Topics.oppdrag)

    oppdrag
        .map { oppdrag -> StatusReply(status = Status.HOS_OPPDRAG) }
        .produce(Topics.status)

    oppdrag.forEach { _, xml ->
        oppdragProducer.send(xml)
    }
}

