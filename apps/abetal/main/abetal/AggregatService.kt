package abetal

import libs.kafka.StreamsPair
import models.Action
import models.PeriodeId
import models.Utbetaling
import models.notFound
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.trygdeetaten.skjema.oppdrag.Oppdrag

object AggregateService {
    fun utledOppdrag(aggregate: List<StreamsPair<Utbetaling, Utbetaling?>>): List<Pair<Oppdrag, List<Utbetaling>>> {

        val utbetalingToOppdrag: List<Pair<Utbetaling, Oppdrag>> = aggregate
            .filter { (new, prev) -> prev == null || new.perioder != prev.perioder }
            .map { (new, prev) ->
                new.validate()

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
            .map { (_, group) -> group.reduce { acc, next -> acc + next } }

        val utbetalinger = utbetalingToOppdrag.map { it.first }

        val oppdragToUtbetalinger = oppdrag
            .map { o -> o to utbetalinger.filter { it.sakId.id == o.oppdrag110.fagsystemId } }

        return oppdragToUtbetalinger
    }

    fun utledSimulering(aggregate: List<StreamsPair<Utbetaling, Utbetaling?>>): List<SimulerBeregningRequest> {
        val simuleringer: List<SimulerBeregningRequest> = aggregate
            .filter { (new, prev) -> prev == null || new.perioder != prev.perioder }
            .map { (new, prev) ->
                new.validate()
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

        return simuleringerPerSak
    }
}

// TODO: addAll fungerer ikke, vi må fjerne "duplikater". Dvs alt (??) er likt unntatt delytelseId (??) og henvisning
operator fun Oppdrag.plus(other: Oppdrag): Oppdrag {
    if (oppdrag110.kodeEndring != "NY") oppdrag110.kodeEndring = other.oppdrag110.kodeEndring
    oppdrag110.oppdragsLinje150s.addAll(other.oppdrag110.oppdragsLinje150s)
    return this
}

operator fun SimulerBeregningRequest.plus(other: SimulerBeregningRequest): SimulerBeregningRequest {
    if (request.oppdrag.kodeEndring != "NY") request.oppdrag.kodeEndring = other.request.oppdrag.kodeEndring
    request.oppdrag.oppdragslinjes.addAll(other.request.oppdrag.oppdragslinjes)
    return this
}

