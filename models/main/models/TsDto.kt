package models

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class TsDto(
    val dryrun: Boolean = false,
    val sakId: String,
    val behandlingId: String,
    val personident: String,
    val vedtakstidspunkt: LocalDateTime,
    val periodetype: Periodetype,
    val saksbehandler: String? = null,
    val beslutter: String? = null,
    val utbetalinger: List<TsUtbetaling>,
)

data class TsUtbetaling(
    val id: UUID,
    val stønad: StønadTypeTilleggsstønader,
    val perioder: List<TsPeriode>,
    val brukFagområdeTillst: Boolean = false,
)

data class TsPeriode(
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

data class TsTuple(val transactionId: String, val dto: TsDto)

fun TsTuple.toDomain(uidsPåSak: Set<UtbetalingId>?): List<Utbetaling> {
    return dto.utbetalinger.map { utbetaling ->
        if (utbetaling.brukFagområdeTillst && utbetaling.stønad !in stønadstyperForTillst) {
            badRequest("Fant stønadstype ${utbetaling.stønad} ved bruk av fagområde TILLST. Tillater bare en av: $stønadstyperForTillst")
        }
        val (action, perioder) = when (utbetaling.perioder.isEmpty()) {
            true -> Action.DELETE to listOf(Utbetalingsperiode(LocalDate.now(), LocalDate.now(), 1u))
            false -> Action.CREATE to utbetaling.perioder.toDomain(dto.periodetype)
        }
        Utbetaling(
            dryrun = dto.dryrun,
            originalKey = transactionId,
            fagsystem = utbetaling.fagsystem(),
            uid = UtbetalingId(utbetaling.id),
            action = action,
            førsteUtbetalingPåSak = uidsPåSak == null,
            sakId = SakId(dto.sakId),
            behandlingId = BehandlingId(dto.behandlingId),
            lastPeriodeId = PeriodeId(),
            personident = Personident(dto.personident),
            vedtakstidspunkt = dto.vedtakstidspunkt,
            stønad = utbetaling.stønad,
            beslutterId = dto.beslutter?.let(::Navident) ?: Navident("ts"),
            saksbehandlerId = dto.saksbehandler?.let(::Navident) ?: Navident("ts"),
            periodetype = dto.periodetype,
            avvent = null,
            perioder = perioder,
        )
    }
}

/**
* For å støtte tidligere saker som brukte fagområde TILLST,
* må vi fortsette å bruke Fagområde TILLST, det vil være feil
* å bruke TILLST på andre stønadstyper.
* Vi klarer ikke håndheve dette for nye saker men ser det
* lite sansynlig at det vil skje.
*/
private val stønadstyperForTillst = listOf(
    StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
    StønadTypeTilleggsstønader.TILSYN_BARN_AAP,
    StønadTypeTilleggsstønader.TILSYN_BARN_ETTERLATTE,
    StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER,
    StønadTypeTilleggsstønader.LÆREMIDLER_AAP,
    StønadTypeTilleggsstønader.LÆREMIDLER_ETTERLATTE,
    StønadTypeTilleggsstønader.BOUTGIFTER_AAP,
    StønadTypeTilleggsstønader.BOUTGIFTER_ENSLIG_FORSØRGER,
    StønadTypeTilleggsstønader.BOUTGIFTER_ETTERLATTE,
) 

/**
* Tilleggsstønader har fler fagområder fordi man ikke skal kunne motregne
* uavhengige stønadstyper mot hverandre.
*/
fun TsUtbetaling.fagsystem(): Fagsystem {
    return when (brukFagområdeTillst) {
        true -> Fagsystem.TILLEGGSSTØNADER
        else -> when (stønad) {
            StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER       -> Fagsystem.TILLSTPB
            StønadTypeTilleggsstønader.TILSYN_BARN_AAP                    -> Fagsystem.TILLSTPB
            StønadTypeTilleggsstønader.TILSYN_BARN_ETTERLATTE             -> Fagsystem.TILLSTPB
            StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER        -> Fagsystem.TILLSTLM
            StønadTypeTilleggsstønader.LÆREMIDLER_AAP                     -> Fagsystem.TILLSTLM
            StønadTypeTilleggsstønader.LÆREMIDLER_ETTERLATTE              -> Fagsystem.TILLSTLM
            StønadTypeTilleggsstønader.BOUTGIFTER_AAP                     -> Fagsystem.TILLSTBO
            StønadTypeTilleggsstønader.BOUTGIFTER_ENSLIG_FORSØRGER        -> Fagsystem.TILLSTBO
            StønadTypeTilleggsstønader.BOUTGIFTER_ETTERLATTE              -> Fagsystem.TILLSTBO
            StønadTypeTilleggsstønader.DAGLIG_REISE_ENSLIG_FORSØRGET      -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_AAP                   -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_ETTERLATTE            -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.REISE_TIL_SAMLING_ENSLIG_FORSØRGER -> Fagsystem.TILLSTRS
            StønadTypeTilleggsstønader.REISE_TIL_SAMLING_AAP              -> Fagsystem.TILLSTRS
            StønadTypeTilleggsstønader.REISE_TIL_SAMLING_ETTERLATTE       -> Fagsystem.TILLSTRS
            StønadTypeTilleggsstønader.REISE_OPPSTART_ENSLIG_FORSØRGET    -> Fagsystem.TILLSTRO
            StønadTypeTilleggsstønader.REISE_OPPSTART_AAP                 -> Fagsystem.TILLSTRO
            StønadTypeTilleggsstønader.REISE_OPPSTART_ETTERLATTE          -> Fagsystem.TILLSTRO
            StønadTypeTilleggsstønader.REIS_ARBEID_ENSLIG_FORSØRGER       -> Fagsystem.TILLSTRA 
            StønadTypeTilleggsstønader.REIS_ARBEID_AAP                    -> Fagsystem.TILLSTRA
            StønadTypeTilleggsstønader.REIS_ARBEID_ETTERLATTE             -> Fagsystem.TILLSTRA
            StønadTypeTilleggsstønader.FLYTTING_ENSLIG_FORSØRGER          -> Fagsystem.TILLSTFL
            StønadTypeTilleggsstønader.FLYTTING_AAP                       -> Fagsystem.TILLSTFL
            StønadTypeTilleggsstønader.FLYTTING_ETTERLATTE                -> Fagsystem.TILLSTFL
        }
    }
}

private fun List<TsPeriode>.toDomain(type: Periodetype): List<Utbetalingsperiode> {
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

        else -> badRequest("periodetype '$type' for tilleggsstønader er ikke implementert")
    }
}

