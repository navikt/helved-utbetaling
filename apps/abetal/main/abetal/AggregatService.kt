package abetal

import libs.kafka.StreamsPair
import libs.utils.secureLog
import models.*
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.trygdeetaten.skjema.oppdrag.Oppdrag

object AggregateService {

    private fun hasChanges(pair: StreamsPair<Utbetaling, Utbetaling?>) = pair.let { (new, prev) ->
        prev == null ||
        prev.action in listOf(Action.DELETE, Action.FAKE_DELETE) && new.action == Action.CREATE ||
        new.perioder != prev.perioder
    }

    fun utledOppdrag(aggregate: List<StreamsPair<Utbetaling, Utbetaling?>>): List<Pair<Oppdrag, List<Utbetaling>>> {
        val aggregate = aggregate.filter { (new, _) -> !new.dryrun }

        aggregate.forEach { (new, prev) ->
            prev?.preValidateLockedFields(new)
        }

        val utbetalingToOppdrag: List<Pair<Utbetaling, Oppdrag>> = aggregate
            .filter(::hasChanges)
            .map { (new, prev) ->
                new.validate()

                when {
                    new.action == Action.DELETE -> {
                        val prev = prev ?: notFound("previous utbetaling for ${new.uid.id}")
                        val oppdrag = OppdragService.delete(new, prev)
                        val lastPeriodeId = PeriodeId.decode(oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId)
                        val utbetaling = prev.copy(action = Action.DELETE, lastPeriodeId = lastPeriodeId)
                        secureLog.debug("opphør utbetaling ${new.uid}")
                        utbetaling to oppdrag
                    }

                    new.action == Action.FAKE_DELETE -> {
                        val prev = prev ?: notFound("previous utbetaling for ${new.uid.id}")
                        val new = prev.copy(behandlingId = new.behandlingId)
                        val oppdrag = OppdragService.delete(new, prev) // new is a fakeDelete
                        val lastPeriodeId = PeriodeId.decode(oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId)
                        val utbetaling = prev.copy(action = Action.DELETE, lastPeriodeId = lastPeriodeId)
                        secureLog.debug("opphør utbetaling ${new.uid}")
                        utbetaling to oppdrag
                    }

                    // reintroduser en tidligere opphørt utbetaling
                    prev?.action == Action.DELETE && new.action == Action.CREATE -> {
                        val oppdrag = OppdragService.opprett(new, prev.lastPeriodeId)
                        val lastPeriodeId = PeriodeId.decode(oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId)
                        val utbetaling = new.copy(action = Action.CREATE, lastPeriodeId = lastPeriodeId)
                        secureLog.debug("reintroduser en tidligere opphørt utbetaling ${new.uid}")
                        utbetaling to oppdrag
                    }

                    prev == null -> {
                        val oppdrag = OppdragService.opprett(new)
                        val lastPeriodeId = PeriodeId.decode(oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId)
                        val utbetaling = new.copy(action = Action.CREATE, lastPeriodeId = lastPeriodeId)
                        secureLog.debug("opprett utbetaling ${new.uid}")
                        utbetaling to oppdrag
                    }

                    else -> {
                        val oppdrag = OppdragService.update(new, prev)
                        val lastPeriodeId = PeriodeId.decode(oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId)
                        val utbetaling = new.copy(action = Action.UPDATE, lastPeriodeId = lastPeriodeId)
                        secureLog.debug("endre utbetaling ${new.uid}")
                        utbetaling to oppdrag
                    }
                }
            }

        val oppdrager = utbetalingToOppdrag.map { it.second }
        if (oppdrager.isEmpty()) return emptyList()

        val oppdrag = oppdrager.reduce { acc, next -> acc + next }
        val utbetalinger = utbetalingToOppdrag.map { it.first }

        return listOf(oppdrag to utbetalinger)
    }

    fun utledSimulering(aggregate: List<StreamsPair<Utbetaling, Utbetaling?>>): Pair<Boolean, List<SimulerBeregningRequest>> {
        val aggregate = aggregate.filter { (new, _) -> new.dryrun }

        if (aggregate.isNotEmpty() && aggregate.none(::hasChanges)) return aggregate.any() to emptyList()

        val simuleringer = aggregate
            .filter(::hasChanges)
            .map { (new, prev) ->
                new.validate()
                when {
                    new.action == Action.DELETE -> {
                        secureLog.info("simuler opphør for $prev")
                        val prev = prev ?: notFound("previous utbetaling for ${new.uid.id}")
                        SimuleringService.delete(prev, prev)
                    }

                    // reintroduser en tidligere opphørt utbetaling
                    prev?.action == Action.DELETE && new.action == Action.CREATE -> {
                        secureLog.info("simuler recreate for $new")
                        SimuleringService.opprett(new, prev.lastPeriodeId)
                    }

                    prev == null -> {
                        secureLog.info("simuler opprett for $new")
                        SimuleringService.opprett(new)
                    }

                    else -> {
                        secureLog.info("simuler endring for $prev -> $new")
                        SimuleringService.update(new, prev)
                    }
                }
            }

        val simuleringerPerSak = simuleringer.groupBy { it.request.oppdrag.fagsystemId.trimEnd() }
            .map { (_, group) -> group.reduce { acc, next -> acc + next } }

        return aggregate.any() to simuleringerPerSak
    }
}

operator fun Oppdrag.plus(other: Oppdrag): Oppdrag {
    if (oppdrag110.kodeEndring != "NY") oppdrag110.kodeEndring = other.oppdrag110.kodeEndring
    require(oppdrag110.fagsystemId == other.oppdrag110.fagsystemId)
    val otherOppdrag150s = other.oppdrag110.oppdragsLinje150s
    oppdrag110.oppdragsLinje150s.addAll(otherOppdrag150s)
    return this
}

operator fun SimulerBeregningRequest.plus(other: SimulerBeregningRequest): SimulerBeregningRequest {
    if (request.oppdrag.kodeEndring != "NY") request.oppdrag.kodeEndring = other.request.oppdrag.kodeEndring
    request.oppdrag.oppdragslinjes.addAll(other.request.oppdrag.oppdragslinjes)
    return this
}

