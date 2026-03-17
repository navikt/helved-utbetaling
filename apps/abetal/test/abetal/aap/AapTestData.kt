package abetal.aap

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import models.*
import abetal.*

fun MutableList<DetaljerLinje>.linje(
    behandlingId: BehandlingId,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    utbetaltBeløp: UInt = sats,
    klassekode: String = "AAPOR",
) {
    add(DetaljerLinje(behandlingId.id, fom, tom, sats, utbetaltBeløp, klassekode))
}

fun MutableList<AapUtbetalingsdag>.meldekort(
    meldeperiode: String,
    fom: LocalDate,
    tom: LocalDate,
    utbetaltBeløp: UInt,
    sats: UInt,
) {
    add(AapUtbetalingsdag(meldeperiode, fom, tom, sats, utbetaltBeløp))
}

object Aap {
    fun utbetaling(
        sakId: String = "$nextInt",
        behandlingId: String = "$nextInt",
        dryrun: Boolean = false,
        ident: String = "12345678910",
        vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
        avvent: Avvent? = null,
        utbetalinger: MutableList<AapUtbetalingsdag>.() -> Unit,
    ): AapUtbetaling = AapUtbetaling(
        dryrun = dryrun,
        behandlingId = behandlingId,
        sakId = sakId,
        ident = ident,
        vedtakstidspunktet = vedtakstidspunkt,
        utbetalinger = mutableListOf<AapUtbetalingsdag>().apply(utbetalinger),
        avvent = avvent,
    )

    fun meldekort(
        meldeperiode: String,
        fom: LocalDate,
        tom: LocalDate,
        sats: UInt,
        utbetaltBeløp: UInt = sats,
    ): List<AapUtbetalingsdag> {
        return listOf(AapUtbetalingsdag(meldeperiode, fom, tom, sats, utbetaltBeløp))
    }

    fun mottatt(linjer: MutableList<DetaljerLinje>.() -> Unit): StatusReply {
        return StatusReply(
            Status.MOTTATT,
            Detaljer(Fagsystem.AAP, mutableListOf<DetaljerLinje>().apply(linjer))
        )
    }
}
