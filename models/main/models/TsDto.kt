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
) {
    companion object {
        fun toDomain(
            sakId: SakId,
            originalKey: String,
            tsDto: TsDto,
            uidsPåSak: Set<UtbetalingId>?,
        ): List<Utbetaling> {
            return tsDto.utbetalinger.map { utbetaling ->
                if (utbetaling.brukFagområdeTillst && utbetaling.stønad !in stønadstyperForTillst) {
                    badRequest("Fant stønadstype ${utbetaling.stønad} ved bruk av fagområde TILLST. Tillater bare en av: $stønadstyperForTillst")
                }
                val (action, perioder) = when (utbetaling.perioder.isEmpty()) {
                    true -> Action.DELETE to listOf(Utbetalingsperiode(LocalDate.now(), LocalDate.now(), 1u))
                    false -> Action.CREATE to utbetaling.perioder.toDomain(tsDto.periodetype)
                }
                Utbetaling(
                    dryrun = tsDto.dryrun,
                    originalKey = originalKey,
                    fagsystem = utbetaling.fagsystem(),
                    uid = UtbetalingId(utbetaling.id),
                    action = action,
                    førsteUtbetalingPåSak = uidsPåSak == null,
                    sakId = sakId,
                    behandlingId = BehandlingId(tsDto.behandlingId),
                    lastPeriodeId = PeriodeId(),
                    personident = Personident(tsDto.personident),
                    vedtakstidspunkt = tsDto.vedtakstidspunkt,
                    stønad = utbetaling.stønad,
                    beslutterId = tsDto.beslutter?.let(::Navident) ?: Navident("ts"),
                    saksbehandlerId = tsDto.saksbehandler?.let(::Navident) ?: Navident("ts"),
                    periodetype = tsDto.periodetype,
                    avvent = null,
                    perioder = perioder,
                )
            }
        }
    }
}

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
    val betalendeEnhet: NavEnhet? = null,
) {
    fun into(): Utbetalingsperiode = Utbetalingsperiode(
        fom = fom,
        tom = tom,
        beløp = beløp,
        betalendeEnhet = betalendeEnhet,
    )
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

    StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_ARBEIDSFORBEREDENDE, // ARBFORB - TSDRAFT-OP
    StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_ARBEIDSRETTET_REHAB, // ARBRRHDAG - TSDRARREHABAGDAG-OP
    StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_ARBEIDSTRENING, // ARBTREN - TSDRATTT2-OP
    StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_AVKLARING, // AVKLARAG - TSDRAAG-OP
    StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_DIGITAL_JOBBKLUBB, // DIGIOPPARB - TSDRDIGJK-OP
    StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_ENKELTPLASS_AMO, // ENKELAMO - TSDREPAMO-OP
    StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_ENKELTPLASS_FAG_YRKE_HOYERE_UTD, // ENKFAGYRKE - TSDREPVGSHOY-OP
    StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_FORSØK_OPPLÆRINGSTILTAK_LENGER_VARIGHET, // FORSOPPLEV - TSDRFOLV
    StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_GRUPPE_AMO, // GRUPPEAMO - TSDRGRAMO-OP
    StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_GRUPPE_FAG_YRKE_HOYERE_UTD, // GRUFAGYRKE - TSDRGRVGSHOY-OP
    StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_HØYERE_UTDANNING, // HOYEREUTD - TSDRHOYUTD-OP
    StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_INDIVIDUELL_JOBBSTØTTE, // INDJOBSTOT - TSDRIPS-OP
    StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_INDIVIDUELL_JOBBSTØTTE_UNG, // IPSUNG - TSDRIPSUNG-OP
    StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_JOBBKLUBB, // JOBBK - TSDRJB2009-OP
    StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_OPPFØLGING, // INDOPPFAG - TSDROPPFAG2-OP
    StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_UTVIDET_OPPFØLGING_I_NAV, // UTVAOONAV - TSDRUTVAVKLOPPF-OP
    StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_UTVIDET_OPPFØLGING_I_OPPLÆRING, // UTVOPPFOPL - TSDRUTVOPPFOPPL-OP
)

/**
 * Tilleggsstønader har fler fagområder fordi man ikke skal kunne motregne
 * uavhengige stønadstyper mot hverandre.
 */
fun TsUtbetaling.fagsystem(): Fagsystem {
    return when (brukFagområdeTillst) {
        true -> Fagsystem.TILLEGGSSTØNADER
        else -> when (stønad) {
            StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER -> Fagsystem.TILLSTPB
            StønadTypeTilleggsstønader.TILSYN_BARN_AAP -> Fagsystem.TILLSTPB
            StønadTypeTilleggsstønader.TILSYN_BARN_ETTERLATTE -> Fagsystem.TILLSTPB
            StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER -> Fagsystem.TILLSTLM
            StønadTypeTilleggsstønader.LÆREMIDLER_AAP -> Fagsystem.TILLSTLM
            StønadTypeTilleggsstønader.LÆREMIDLER_ETTERLATTE -> Fagsystem.TILLSTLM
            StønadTypeTilleggsstønader.BOUTGIFTER_AAP -> Fagsystem.TILLSTBO
            StønadTypeTilleggsstønader.BOUTGIFTER_ENSLIG_FORSØRGER -> Fagsystem.TILLSTBO
            StønadTypeTilleggsstønader.BOUTGIFTER_ETTERLATTE -> Fagsystem.TILLSTBO
            @Suppress("Deprecation")
            StønadTypeTilleggsstønader.DAGLIG_REISE_ENSLIG_FORSØRGET -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_ENSLIG_FORSØRGER -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_AAP -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_ETTERLATTE -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.REISE_TIL_SAMLING_ENSLIG_FORSØRGER -> Fagsystem.TILLSTRS
            StønadTypeTilleggsstønader.REISE_TIL_SAMLING_AAP -> Fagsystem.TILLSTRS
            StønadTypeTilleggsstønader.REISE_TIL_SAMLING_ETTERLATTE -> Fagsystem.TILLSTRS
            @Suppress("Deprecation")
            StønadTypeTilleggsstønader.REISE_OPPSTART_ENSLIG_FORSØRGET -> Fagsystem.TILLSTRO
            StønadTypeTilleggsstønader.REISE_OPPSTART_ENSLIG_FORSØRGER -> Fagsystem.TILLSTRO
            StønadTypeTilleggsstønader.REISE_OPPSTART_AAP -> Fagsystem.TILLSTRO
            StønadTypeTilleggsstønader.REISE_OPPSTART_ETTERLATTE -> Fagsystem.TILLSTRO
            StønadTypeTilleggsstønader.REIS_ARBEID_ENSLIG_FORSØRGER -> Fagsystem.TILLSTRA
            StønadTypeTilleggsstønader.REIS_ARBEID_AAP -> Fagsystem.TILLSTRA
            StønadTypeTilleggsstønader.REIS_ARBEID_ETTERLATTE -> Fagsystem.TILLSTRA
            StønadTypeTilleggsstønader.FLYTTING_ENSLIG_FORSØRGER -> Fagsystem.TILLSTFL
            StønadTypeTilleggsstønader.FLYTTING_AAP -> Fagsystem.TILLSTFL
            StønadTypeTilleggsstønader.FLYTTING_ETTERLATTE -> Fagsystem.TILLSTFL

            StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_ARBEIDSFORBEREDENDE -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_ARBEIDSRETTET_REHAB -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_ARBEIDSTRENING -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_AVKLARING -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_DIGITAL_JOBBKLUBB -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_ENKELTPLASS_AMO -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_ENKELTPLASS_FAG_YRKE_HOYERE_UTD -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_FORSØK_OPPLÆRINGSTILTAK_LENGER_VARIGHET -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_GRUPPE_AMO -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_GRUPPE_FAG_YRKE_HOYERE_UTD -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_HØYERE_UTDANNING -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_INDIVIDUELL_JOBBSTØTTE -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_INDIVIDUELL_JOBBSTØTTE_UNG -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_JOBBKLUBB -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_OPPFØLGING -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_UTVIDET_OPPFØLGING_I_NAV -> Fagsystem.TILLSTDR
            StønadTypeTilleggsstønader.DAGLIG_REISE_TILTAK_UTVIDET_OPPFØLGING_I_OPPLÆRING -> Fagsystem.TILLSTDR
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
                betalendeEnhet = it.betalendeEnhet,
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
                        betalendeEnhet = it.first().betalendeEnhet,
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
                        betalendeEnhet = it.first().betalendeEnhet,
                    )
                }
            }
            .flatten()
            .sortedBy { it.fom }

        else -> badRequest("periodetype '$type' for tilleggsstønader er ikke implementert")
    }
}

