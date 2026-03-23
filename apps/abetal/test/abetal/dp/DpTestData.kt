package abetal.dp

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import models.*
import abetal.*
import models.kontrakter.objectMapper

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
    addAll(buildList {
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
        return buildList {
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

const val meldeperiode = "2025-08-01-2025-08-14"
val dagpengerMeldeperiodeDager = listOf(
    DpUtbetalingsdag(
        meldeperiode = meldeperiode,
        dato = LocalDate.of(2025, 8, 4),
        sats = 1000u,
        utbetaltBeløp = 1000u,
        utbetalingstype = Utbetalingstype.Dagpenger
    ),
    DpUtbetalingsdag(
        meldeperiode = meldeperiode,
        dato = LocalDate.of(2025, 8, 5),
        sats = 1000u,
        utbetaltBeløp = 1000u,
        utbetalingstype = Utbetalingstype.Dagpenger
    ),
    DpUtbetalingsdag(
        meldeperiode = meldeperiode,
        dato = LocalDate.of(2025, 8, 6),
        sats = 1000u,
        utbetaltBeløp = 1000u,
        utbetalingstype = Utbetalingstype.Dagpenger
    ),
    DpUtbetalingsdag(
        meldeperiode = meldeperiode,
        dato = LocalDate.of(2025, 8, 8),
        sats = 1000u,
        utbetaltBeløp = 1000u,
        utbetalingstype = Utbetalingstype.Dagpenger
    ),
    DpUtbetalingsdag(
        meldeperiode = meldeperiode,
        dato = LocalDate.of(2025, 8, 9),
        sats = 1000u,
        utbetaltBeløp = 1000u,
        utbetalingstype = Utbetalingstype.Dagpenger
    ),
    DpUtbetalingsdag(
        meldeperiode = meldeperiode,
        dato = LocalDate.of(2025, 8, 10),
        sats = 1000u,
        utbetaltBeløp = 1000u,
        utbetalingstype = Utbetalingstype.Dagpenger
    ),
    DpUtbetalingsdag(
        meldeperiode = meldeperiode,
        dato = LocalDate.of(2025, 8, 11),
        sats = 1000u,
        utbetaltBeløp = 1000u,
        utbetalingstype = Utbetalingstype.Dagpenger
    ),
    DpUtbetalingsdag(
        meldeperiode = meldeperiode,
        dato = LocalDate.of(2025, 8, 12),
        sats = 1000u,
        utbetaltBeløp = 1000u,
        utbetalingstype = Utbetalingstype.Dagpenger
    ),
    DpUtbetalingsdag(
        meldeperiode = meldeperiode,
        dato = LocalDate.of(2025, 8, 13),
        sats = 1000u,
        utbetaltBeløp = 1000u,
        utbetalingstype = Utbetalingstype.Dagpenger
    ),
    DpUtbetalingsdag(
        meldeperiode = meldeperiode,
        dato = LocalDate.of(2025, 8, 14),
        sats = 1000u,
        utbetaltBeløp = 1000u,
        utbetalingstype = Utbetalingstype.Dagpenger
    ),
    DpUtbetalingsdag(
        meldeperiode = meldeperiode,
        dato = LocalDate.of(2025, 8, 7),
        sats = 1000u,
        utbetaltBeløp = 700u,
        utbetalingstype = Utbetalingstype.Dagpenger
    )
)

internal fun DpUtbetaling.asBytes() = objectMapper.writeValueAsBytes(this)