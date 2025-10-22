package abetal

import libs.kafka.StreamsPair
import libs.utils.appLog
import models.Action
import models.PeriodeId
import models.Utbetaling
import models.notFound
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import java.math.BigDecimal
import javax.xml.datatype.XMLGregorianCalendar
import models.badRequest

object AggregateService {
    fun utledOppdrag(aggregate: List<StreamsPair<Utbetaling, Utbetaling?>>): List<Pair<Oppdrag, List<Utbetaling>>> {

        val utbetalingToOppdrag: List<Pair<Utbetaling, Oppdrag>> =
            aggregate.filter { (new, prev) -> prev == null || new.perioder != prev.perioder }.map { (new, prev) ->
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

        val kjeder = utbetalingToOppdrag.groupBy { it.first.uid }.map { (_, kjede) -> kjede.reduce { acc, next -> acc + next } }
        val oppdrager = kjeder.map { it.second }
        if (oppdrager.isEmpty()) return emptyList()
        val oppdrag = oppdrager.reduce { acc, next -> acc + next }

        val utbetalinger = kjeder.map { it.first }

        return listOf(oppdrag to utbetalinger)
    }

    fun utledSimulering(aggregate: List<StreamsPair<Utbetaling, Utbetaling?>>): List<SimulerBeregningRequest> {
        val hasChanges: (StreamsPair<Utbetaling, Utbetaling?>) -> Boolean = { (new, prev) ->
            prev == null || new.perioder != prev.perioder
        }

        if (aggregate.isNotEmpty() && aggregate.none(hasChanges)) return emptyList()

        val simuleringer = aggregate
            .filter(hasChanges)
            .map { (new, prev) ->
                new.validate()
                when {
                    new.action == Action.DELETE -> {
                        appLog.info("simuler opphør for $prev")
                        val prev = prev ?: notFound("previous utbetaling for ${new.uid.id}")
                        SimuleringService.delete(prev, prev)
                    }

                    prev == null -> {
                        appLog.info("simuler opprett for $new")
                        SimuleringService.opprett(new)
                    }

                    else -> {
                        appLog.info("simuler endring for $prev -> $new")
                        SimuleringService.update(new, prev)
                    }
                }
            }

        val simuleringerPerSak = simuleringer.groupBy { it.request.oppdrag.fagsystemId.trimEnd() }
            .map { (_, group) -> group.reduce { acc, next -> acc + next } }

        return simuleringerPerSak
    }
}

data class Oppdrag150(
    val vedtakId: String,
    val fom: XMLGregorianCalendar,
    val tom: XMLGregorianCalendar,
    val kodeKlassifik: String,
    val sats: BigDecimal,
    val vedtakssats: BigDecimal?
)

operator fun Pair<Utbetaling, Oppdrag>.plus(other: Pair<Utbetaling, Oppdrag>): Pair<Utbetaling, Oppdrag> {
    return (first noe other.first) to (second kjed other.second)
}

infix fun Utbetaling.noe(other: Utbetaling): Utbetaling {
    return this.copy(
        perioder = (perioder union other.perioder).toList()
    )
}

operator fun Utbetaling.plus(other: Utbetaling): Utbetaling {
    return this.copy(
        behandlingId = other.behandlingId,
        perioder = (perioder union other.perioder).toList()
    )
}

infix fun Oppdrag.kjed(other: Oppdrag): Oppdrag {
    if (oppdrag110.kodeEndring != "NY") oppdrag110.kodeEndring = other.oppdrag110.kodeEndring
    val currentOppdrag150s = oppdrag110.oppdragsLinje150s.map { it ->
        Oppdrag150(
            it.vedtakId, it.datoVedtakFom, it.datoVedtakTom, it.kodeKlassifik, it.sats, it.vedtakssats157?.vedtakssats
        )
    }

    val otherOppdrag150s = other.oppdrag110.oppdragsLinje150s.filter {
        val oppdrag150 = Oppdrag150(
            it.vedtakId, it.datoVedtakFom, it.datoVedtakTom, it.kodeKlassifik, it.sats, it.vedtakssats157?.vedtakssats
        )
        oppdrag150 !in currentOppdrag150s
    }
    if (otherOppdrag150s.isNotEmpty()) {
        otherOppdrag150s.first().refDelytelseId = this.oppdrag110.oppdragsLinje150s.last().delytelseId
    }

    oppdrag110.oppdragsLinje150s.addAll(otherOppdrag150s)
    return this
}

operator fun Oppdrag.plus(other: Oppdrag): Oppdrag {
    if (oppdrag110.kodeEndring != "NY") oppdrag110.kodeEndring = other.oppdrag110.kodeEndring
    require(oppdrag110.fagsystemId == other.oppdrag110.fagsystemId)
    val currentOppdrag150s = oppdrag110.oppdragsLinje150s.map { it ->
        Oppdrag150(
            it.vedtakId, it.datoVedtakFom, it.datoVedtakTom, it.kodeKlassifik, it.sats, it.vedtakssats157?.vedtakssats
        )
    }

    val otherOppdrag150s = other.oppdrag110.oppdragsLinje150s.filter {
        val oppdrag150 = Oppdrag150(
            it.vedtakId, it.datoVedtakFom, it.datoVedtakTom, it.kodeKlassifik, it.sats, it.vedtakssats157?.vedtakssats
        )
        oppdrag150 !in currentOppdrag150s
    }

    oppdrag110.oppdragsLinje150s.addAll(otherOppdrag150s)
    return this
}

operator fun SimulerBeregningRequest.plus(other: SimulerBeregningRequest): SimulerBeregningRequest {
    if (request.oppdrag.kodeEndring != "NY") request.oppdrag.kodeEndring = other.request.oppdrag.kodeEndring
    request.oppdrag.oppdragslinjes.addAll(other.request.oppdrag.oppdragslinjes)
    return this
}

