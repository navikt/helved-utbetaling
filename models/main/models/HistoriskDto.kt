package models

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class HistoriskUtbetaling(
    val dryrun: Boolean = false,
    val id: UUID,
    val sakId: String,
    val behandlingId: String,
    val personident: String,
    val stønad: StønadTypeHistorisk,
    val vedtakstidspunkt: LocalDateTime,
    val periodetype: Periodetype,
    val perioder: List<HistoriskPeriode>,
    val saksbehandler: String? = null,
    val beslutter: String? = null,
    )

data class HistoriskPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: UInt,
) {
    fun into(): Utbetalingsperiode = Utbetalingsperiode(
        fom = fom,
        tom = tom,
        beløp = beløp,
    )
}

data class HistoriskTuple(val transactionId: String, val dto: HistoriskUtbetaling)

fun HistoriskTuple.toDomain(uidsPåSak: Set<UtbetalingId>?): Utbetaling {
    val (action, perioder) = when (dto.perioder.isEmpty()) {
        true -> Action.DELETE to listOf(Utbetalingsperiode(LocalDate.now(), LocalDate.now(), 1u)) // Dummy-periode for validering
        false -> Action.CREATE to dto.perioder.toDomain(dto.periodetype)
    }

    return Utbetaling(
        dryrun = dto.dryrun,
        originalKey = transactionId,
        fagsystem = Fagsystem.HISTORISK,
        uid = UtbetalingId(dto.id),
        action = action,
        førsteUtbetalingPåSak = uidsPåSak == null,
        sakId = SakId(dto.sakId),
        behandlingId = BehandlingId(dto.behandlingId),
        lastPeriodeId = PeriodeId(),
        personident = Personident(dto.personident),
        vedtakstidspunkt = dto.vedtakstidspunkt,
        stønad = dto.stønad,
        beslutterId = dto.beslutter?.let(::Navident) ?: Navident("historisk"),
        saksbehandlerId = dto.saksbehandler?.let(::Navident) ?: Navident("historisk"),
        periodetype = dto.periodetype,
        avvent = null,
        perioder = perioder,
    )
}

private fun List<HistoriskPeriode>.toDomain(type: Periodetype): List<Utbetalingsperiode> {
    return when (type) {
        Periodetype.EN_GANG -> this.map {
            Utbetalingsperiode(
                fom = it.fom,
                tom = it.tom,
                beløp = it.beløp,
            )
        }
        Periodetype.UKEDAG -> this.groupBy { it.beløp }
            .map { (_, perioder) ->
                perioder.splitWhen { cur, next ->
                    val harSammenhengendeDager = cur.tom.plusDays(1).equals(next.fom)
                    val harSammenhengendeUker = cur.tom.nesteUkedag().equals(next.fom)
                    !harSammenhengendeUker && !harSammenhengendeDager
                }.map {
                    Utbetalingsperiode(
                        fom = it.first().fom,
                        tom = it.last().tom,
                        beløp = it.first().beløp,
                    )
                }
            }
            .flatten()
            .sortedBy { it.fom }

        Periodetype.MND -> this.groupBy { it.beløp }
            .map { (_, perioder) ->
                perioder.splitWhen { cur, next ->
                    val harSammenhengendeDager = cur.tom.plusDays(1).equals(next.fom)
                    !harSammenhengendeDager
                }.map {
                    Utbetalingsperiode(
                        fom = it.first().fom,
                        tom = it.last().tom,
                        beløp = it.first().beløp,
                    )
                }
            }
            .flatten()
            .sortedBy { it.fom }

        else -> badRequest("periodetype '$type' for historisk er ikke implementert", doc = "${DocumentedErrors.BASE}/async/api#Historisk")
    }
}
