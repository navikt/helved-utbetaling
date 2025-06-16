package abetal

import libs.kafka.StreamsPair
import libs.utils.secureLog
import models.*
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest

sealed interface Aggregate {
    data class OppdragAggregate(val utbets: List<Utbetaling>, val opps: List<Oppdrag>): Aggregate 
    data class SimuleringAggregate(val sims: List<SimulerBeregningRequest>): Aggregate 
}

object AggregateService {

    fun utledOppdrag(aggregate: List<StreamsPair<Utbetaling, Utbetaling?>>): Aggregate.OppdragAggregate {
        secureLog.trace("oppdrag aggregate: $aggregate")
        val utbetalingToOppdrag: List<Pair<Utbetaling, Oppdrag>> = aggregate.map { (new, prev) ->
            new.validate(prev)

            when {
                new.action == Action.DELETE -> {
                    val prev = prev ?: notFound("previous utbetaling for ${new.uid.id}")
                    val oppdrag = OppdragService.delete(prev, prev) // new is a fakeDelete
                    val lastPeriodeId = PeriodeId.decode(oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId)
                    val utbetaling = prev.copy(action = Action.DELETE, lastPeriodeId = lastPeriodeId)
                    utbetaling to oppdrag
                }
                prev == null -> {
                    val oppdrag = OppdragService.opprett(new) 
                    val lastPeriodeId = PeriodeId.decode(oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId)
                    val utbetaling = new.copy(action = Action.CREATE, lastPeriodeId = lastPeriodeId)
                    utbetaling to oppdrag
                }
                else -> {
                    val oppdrag = OppdragService.update(new, prev)
                    val lastPeriodeId = PeriodeId.decode(oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId)
                    val utbetaling = new.copy(action = Action.UPDATE, lastPeriodeId = lastPeriodeId)
                    utbetaling to oppdrag
                }
            }
        }

        val oppdrag = utbetalingToOppdrag
            .map { it.second }
            .groupBy { it.oppdrag110.fagsystemId!! }
            .map { (_, group) -> group.reduce { acc, next -> acc + next} }

        val utbetalinger = utbetalingToOppdrag.map { it.first }

        return Aggregate.OppdragAggregate(utbetalinger, oppdrag)
    } 

    fun utledSimulering(aggregate: List<StreamsPair<Utbetaling, Utbetaling?>>): Aggregate.SimuleringAggregate {
        secureLog.trace("dryrun aggregate: $aggregate")
        val simuleringer: List<SimulerBeregningRequest> = aggregate.map { (new, prev) ->
            new.validate(prev)
            when {
                new.action == Action.DELETE -> {
                    val prev = prev ?: notFound("previous utbetaling for ${new.uid.id}")
                    SimuleringService.delete(prev, prev)
                }
                prev == null -> SimuleringService.opprett(new)
                else -> SimuleringService.update(new, prev)
            }
        }

        val simuleringerPerSak = simuleringer
            .groupBy { it.request.oppdrag.fagsystemId.trimEnd() }
            .map { (_, group) -> group.reduce { acc, next -> acc + next } }

        return Aggregate.SimuleringAggregate(simuleringerPerSak)
    }
}

operator fun Oppdrag.plus(other: Oppdrag): Oppdrag {
    if(oppdrag110.kodeEndring != "NY") oppdrag110.kodeEndring = other.oppdrag110.kodeEndring 
    oppdrag110.oppdragsLinje150s.addAll(other.oppdrag110.oppdragsLinje150s)
    return this
}

operator fun SimulerBeregningRequest.plus(other: SimulerBeregningRequest): SimulerBeregningRequest {
    if(request.oppdrag.kodeEndring != "NY") request.oppdrag.kodeEndring = other.request.oppdrag.kodeEndring
    request.oppdrag.oppdragslinjes.addAll(other.request.oppdrag.oppdragslinjes)
    return this
}

