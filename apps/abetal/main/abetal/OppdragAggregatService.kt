package abetal

import libs.kafka.StreamsPair
import libs.utils.secureLog
import models.*
import no.trygdeetaten.skjema.oppdrag.Oppdrag

object AggregateOppdragService {
    fun utled(aggregate: List<StreamsPair<Utbetaling, Utbetaling?>>): StreamsPair<List<Utbetaling>, Oppdrag> {
        secureLog.trace("aggregate: $aggregate")
        val utbetalingToOppdrag: List<Pair<Utbetaling, Oppdrag>> = aggregate.map { (new, prev) ->
            new.failOnEmptyPerioder() // vi må tillate tomme perioder pga opphør
            new.failOnÅrsskifte()
            new.failOnDuplicatePerioder()
            new.failOnTomBeforeFom()
            new.failOnIllegalFutureUtbetaling()
            new.failOnTooManyPeriods()
            new.failOnDuplicate(prev)
            new.failOnZeroBeløp()
            new.failOnTooLongSakId()
            new.failOnTooLongBehandlingId()
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
                    val utbetaling = new.copy(lastPeriodeId = lastPeriodeId)
                    utbetaling to oppdrag
                }
                else -> {
                    val oppdrag = OppdragService.update(new, prev)
                    val lastPeriodeId = PeriodeId.decode(oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId)
                    val utbetaling = new.copy(lastPeriodeId = lastPeriodeId)
                    utbetaling to oppdrag
                }
            }
        }

        val oppdrag = utbetalingToOppdrag.map { it.second }.reduce { acc, next -> acc + next }
        val utbetalinger = utbetalingToOppdrag.map { it.first }
        return StreamsPair(utbetalinger, oppdrag)
    } 
}

operator fun Oppdrag.plus(other: Oppdrag): Oppdrag {
    if(oppdrag110.kodeAksjon != "NY") oppdrag110.kodeAksjon = other.oppdrag110.kodeAksjon 
    oppdrag110.oppdragsLinje150s.addAll(other.oppdrag110.oppdragsLinje150s)
    return this
}
