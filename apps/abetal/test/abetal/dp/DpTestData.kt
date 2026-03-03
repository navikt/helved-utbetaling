package abetal.dp

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import no.trygdeetaten.skjema.oppdrag.*
import java.time.format.DateTimeFormatter
import java.math.BigDecimal
import javax.xml.datatype.DatatypeFactory
import java.time.*
import java.util.GregorianCalendar
import javax.xml.datatype.XMLGregorianCalendar
import models.*
import abetal.*

fun MutableList<DetaljerLinje>.linje(
    behandlingId: BehandlingId,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    utbetaltBeløp: UInt = sats,
    klassekode: String = "DAGPENGER",
) {
    add(DetaljerLinje(behandlingId.id, fom, tom, sats, utbetaltBeløp, klassekode))
}

fun MutableList<DpUtbetalingsdag>.meldekort(
    meldeperiode: String,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    utbetaltBeløp: UInt = sats,
    utbetalingstype: Utbetalingstype = Utbetalingstype.Dagpenger,
) {
    addAll(buildList<DpUtbetalingsdag> {
        for(i in 0 ..< ChronoUnit.DAYS.between(fom, tom) + 1) {
            val dato = fom.plusDays(i)
            if (!dato.erHelg()) {
                add(DpUtbetalingsdag(meldeperiode, dato, sats, utbetaltBeløp, utbetalingstype))
            }
        }
    })
}

object Dp {
    fun utbetaling(
        sakId: String = "$nextInt",
        behandlingId: String = "$nextInt",
        dryrun: Boolean = false,
        ident: String = "12345678910",
        vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
        utbetalinger: MutableList<DpUtbetalingsdag>.() -> Unit,
    ): DpUtbetaling = DpUtbetaling(
        dryrun = dryrun,
        behandlingId = behandlingId,
        sakId = sakId,
        ident = ident,
        vedtakstidspunktet = vedtakstidspunkt,
        utbetalinger = mutableListOf<DpUtbetalingsdag>().apply(utbetalinger),
    )

    fun meldekort(
        meldeperiode: String,
        fom: LocalDate,
        tom: LocalDate,
        sats: UInt,
        utbetaltBeløp: UInt = sats,
        utbetalingstype: Utbetalingstype = Utbetalingstype.Dagpenger,
    ): List<DpUtbetalingsdag> {
        return buildList<DpUtbetalingsdag> {
            for(i in 0 ..< ChronoUnit.DAYS.between(fom, tom) + 1) {
                val dato = fom.plusDays(i)
                if (!dato.erHelg()) {
                    add(DpUtbetalingsdag(meldeperiode, dato, sats, utbetaltBeløp, utbetalingstype))
                }
            }
        }
    }
    fun mottatt(linjer: MutableList<DetaljerLinje>.() -> Unit): StatusReply {
        return StatusReply(
            Status.MOTTATT, 
            Detaljer(Fagsystem.DAGPENGER, mutableListOf<DetaljerLinje>().apply(linjer))
        )
    }
}

