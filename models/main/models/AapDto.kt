package models

import libs.utils.appLog
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import models.kontrakter.GyldigBehandlingId
import models.kontrakter.GyldigSakId

data class AapUtbetaling(
    val dryrun: Boolean = false,

    val sakId: String,
    val behandlingId: String,
    val ident: String,
    val utbetalinger: List<AapUtbetalingsdag>,
    val vedtakstidspunktet: LocalDateTime,
    val saksbehandler: String? = null,
    val beslutter: String? = null,
    val avvent: Avvent? = null,
) {
    fun validate() {
        failOnBlankSakId()
        failOnBlankBehandlingId()
        failOnBlankPersonident()
        failOnFutureVedtakstidspunkt()
        failOnEmptyUtbetalinger()
        failOnTomBeforeFom()
        failOnZeroBeløp()
        failOnTooLongSakId()
        failOnTooLongBehandlingId()
    }
}

data class AapUtbetalingsdag(
    val id: UUID,
    val fom: LocalDate,
    val tom: LocalDate,
    val sats: UInt,
    val utbetaltBeløp: UInt,
) {
    fun into(): Utbetalingsperiode = Utbetalingsperiode(
        fom = fom,
        tom = tom,
        beløp = utbetaltBeløp,
        vedtakssats = sats,
    )
}

private fun perioder(perioder: List<AapUtbetalingsdag>): List<Utbetalingsperiode> {
    return perioder.sortedBy { it.fom }
        .groupBy { listOf(it.utbetaltBeløp, it.sats) }
        .map { (_, p) ->
            p.splitWhen { a, b -> 
                val harSammenhengendeDager = a.tom.plusDays(1).equals(b.fom)
                val harSammenhengendeUker = a.tom.nesteUkedag().equals(b.fom)
                !harSammenhengendeUker && !harSammenhengendeDager
            }.map {
                Utbetalingsperiode(
                    fom = it.first().fom,
                    tom = it.last().tom,
                    beløp = it.first().utbetaltBeløp,
                    vedtakssats = it.first().sats,
                )
            }
        }
        .flatten()
        .sortedBy { it.fom }
}

object AapDto {
    fun splitToDomain(
        sakId: SakId,
        originalKey: String,
        aapUtbetaling: AapUtbetaling,
        uids: Set<UtbetalingId>?
    ): List<Utbetaling> {
        val dryrun = aapUtbetaling.dryrun
        val personident = aapUtbetaling.ident
        val behandlingId = aapUtbetaling.behandlingId
        val periodetype = Periodetype.UKEDAG
        val vedtakstidspunktet = aapUtbetaling.vedtakstidspunktet
        val beslutterId = aapUtbetaling.beslutter ?: "kelvin"
        val saksbehandler = aapUtbetaling.saksbehandler ?: "kelvin"
        val utbetalingerPerMeldekort: MutableList<Pair<UtbetalingId, AapUtbetaling?>> = aapUtbetaling
            .utbetalinger
            .groupBy { it.id }
            .map { (id, utbetalinger) -> UtbetalingId(id) to aapUtbetaling.copy(utbetalinger = utbetalinger) }
            .toMutableList()

        if (uids != null) {
            val aapUids = utbetalingerPerMeldekort.map { (uid, _) -> uid }
            val missingMeldeperioder = uids.filter { it !in aapUids }.map { it to null }
            utbetalingerPerMeldekort.addAll(missingMeldeperioder)
        }

        return utbetalingerPerMeldekort.map { (uid, aapUtbetaling) ->
            when (aapUtbetaling) {
                null -> delete(
                    dryrun = dryrun,
                    originalKey = originalKey,
                    sakId = sakId,
                    uid = uid,
                    fagsystem = Fagsystem.AAP,
                    stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
                    beslutterId = Navident(beslutterId),
                    saksbehandlerId = Navident(saksbehandler),
                    personident = Personident(personident),
                    behandlingId = BehandlingId(behandlingId),
                    periodetype = periodetype,
                    vedtakstidspunkt = vedtakstidspunktet,
                ).also { appLog.debug("creating a fake delete to " + "force-trigger a join with existing utbetaling") }

                else -> utbetaling(originalKey, aapUtbetaling, uids, uid)
            }
        }
    }

    private fun utbetaling(
        originalKey: String,
        value: AapUtbetaling,
        uidsPåSak: Set<UtbetalingId>?,
        uid: UtbetalingId,
    ): Utbetaling {
        return Utbetaling(
            dryrun = value.dryrun,
            originalKey = originalKey,
            fagsystem = Fagsystem.AAP,
            uid = uid,
            action = Action.CREATE,
            førsteUtbetalingPåSak = uidsPåSak == null,
            sakId = SakId(value.sakId),
            behandlingId = BehandlingId(value.behandlingId),
            lastPeriodeId = PeriodeId(),
            personident = Personident(value.ident),
            vedtakstidspunkt = value.vedtakstidspunktet,
            stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
            beslutterId = value.beslutter?.let(::Navident) ?: Navident("kelvin"),
            saksbehandlerId = value.saksbehandler?.let(::Navident) ?: Navident("kelvin"),
            periodetype = Periodetype.UKEDAG,
            avvent = value.avvent,
            perioder = perioder(value.utbetalinger),
        )
    }

}

private fun AapUtbetaling.failOnBlankSakId() {
    if (sakId.isBlank()) {
        badRequest(DocumentedErrors.Async.Utbetaling.UGYLDIG_SAK_ID)
    }
}

private fun AapUtbetaling.failOnBlankBehandlingId() {
    if (behandlingId.isBlank()) {
        badRequest(DocumentedErrors.Async.Utbetaling.UGYLDIG_BEHANDLING_ID)
    }
}

private fun AapUtbetaling.failOnBlankPersonident() {
    if (ident.isBlank()) {
        badRequest("Personident kan ikke være tom")
    }
}

private fun AapUtbetaling.failOnFutureVedtakstidspunkt() {
    if (vedtakstidspunktet.isAfter(LocalDateTime.now())) {
        badRequest("Vedtakstidspunkt kan ikke være i fremtiden")
    }
}

private fun AapUtbetaling.failOnEmptyUtbetalinger() {
    if (utbetalinger.isEmpty()) {
        badRequest(DocumentedErrors.Async.Utbetaling.MANGLER_PERIODER)
    }
}

private fun AapUtbetaling.failOnTomBeforeFom() {
    if (!utbetalinger.all { it.fom <= it.tom }) {
        badRequest(DocumentedErrors.Async.Utbetaling.UGYLDIG_PERIODE)
    }
}

private fun AapUtbetaling.failOnZeroBeløp() {
    if (utbetalinger.any { it.utbetaltBeløp == 0u }) {
        badRequest(DocumentedErrors.Async.Utbetaling.UGYLDIG_BELØP)
    }
}

private fun AapUtbetaling.failOnTooLongSakId() {
    if (sakId.length > GyldigSakId.MAKSLENGDE) {
        badRequest(DocumentedErrors.Async.Utbetaling.UGYLDIG_SAK_ID)
    }
}

private fun AapUtbetaling.failOnTooLongBehandlingId() {
    if (behandlingId.length > GyldigBehandlingId.MAKSLENGDE) {
        badRequest(DocumentedErrors.Async.Utbetaling.UGYLDIG_BEHANDLING_ID)
    }
}
