package abetal

import abetal.models.*
import abetal.Result
import libs.kafka.*
import no.trygdeetaten.skjema.oppdrag.Oppdrag

object Topics {
    val aap = Topic<AapUtbetaling>("aap.utbetalinger.v1", JsonSerde.jackson())
    val requests = Topic<UtbetalingRequest>("helved.requests.v1", JsonSerde.jackson())
    val utbetalinger = Topic<Utbetaling>("helved.utbetalinger.v1", JsonSerde.jackson())
    val oppdrag = Topic<Oppdrag>("helved.oppdrag.v1", XmlSerde.serde())
    val status = Topic<StatusReply>("helved.status.v1", JsonSerde.jackson())
}

object Tables {
    val utbetalinger = Table(Topics.utbetalinger)
    val status = Table(Topics.status)
}

fun createTopology(): Topology = topology {
    val utbetalinger = consume(Tables.utbetalinger)
    val status = consume(Tables.status)
    aapStream(status)
    requestStream(utbetalinger)
}

fun Topology.aapStream(status: KTable<StatusReply>) {
    consume(Topics.aap).leftJoinWith(status).map { aapReq, lastStatus ->
        when (lastStatus?.status) {
            null -> {
                val data = aapReq.data.copy(førsteSak = true)
                Result.Ok(UtbetalingRequest(aapReq.action, data))
            }
            Status.OK, Status.FEILET -> {
                val data = aapReq.data.copy(førsteSak = false) // funker ikke hvis den første feila
                Result.Ok(UtbetalingRequest(aapReq.action, data))
            }
            Status.MOTTATT, Status.HOS_OPPDRAG -> {
                Result.Err(lastStatus)
            }
        }
    }.branch( { it.isOk() }) {
        val result = map { it -> it.unwrap() }
        result.produce(Topics.requests)
        result.map { it -> StatusReply(sakId = it.data.sakId) }.produce(Topics.status)
    }.default {
        map { it -> it.unwrapErr() }.produce(Topics.status)
    }
} 

fun Topology.requestStream(utbetalinger: KTable<Utbetaling>) {
    consume(Topics.requests)
        .leftJoinWith(utbetalinger)
        .map { new, prev ->
            Result.catch<Pair<Utbetaling, Oppdrag>>(new.data.sakId) {
                new.data.validate(prev)
                val oppdrag = when (new.action) {
                    Action.CREATE -> OppdragService.opprett(new.data)
                    Action.UPDATE -> OppdragService.update(new.data, prev ?: notFound("previous utbetaling"))
                    Action.DELETE -> OppdragService.delete(new.data, prev ?: notFound("previous utbetaling"))
                }
                val lastPeriodeId = PeriodeId.decode(oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId)
                val new = new.data.copy(lastPeriodeId = lastPeriodeId)
                new to oppdrag
            }
        }.branch({ it.isOk() }) {
            val result =  map { it -> it.unwrap() }
            result.map { (new, _) -> new }.produce(Topics.utbetalinger)
            result.map { (_, oppdrag) -> oppdrag }.produce(Topics.oppdrag)
        }.default {
            map { it -> it.unwrapErr() }.produce(Topics.status)
        }
}
