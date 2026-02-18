package abetal

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

/** 2021*/
val Int.jun21: LocalDate get() = LocalDate.of(2021, 6, this)
/** 2021*/
val Int.jul21: LocalDate get() = LocalDate.of(2021, 7, this)
/** 2021*/
val Int.aug21: LocalDate get() = LocalDate.of(2021, 8, this)
/** 2024*/
val Int.jun: LocalDate get() = LocalDate.of(2024, 6, this)
/** 2024*/
val Int.jul: LocalDate get() = LocalDate.of(2024, 7, this)
/** 2024*/
val Int.aug: LocalDate get() = LocalDate.of(2024, 8, this)
/** 2024*/
val Int.sep: LocalDate get() = LocalDate.of(2024, 9, this)
/** 2024*/
val Int.okt: LocalDate get() = LocalDate.of(2024, 10, this)
/** 2024*/
val Int.des: LocalDate get() = LocalDate.of(2024, 12, this)
/** 2025*/
val Int.jan: LocalDate get() = LocalDate.of(2025, 1, this)
/** 2025*/
val Int.feb25: LocalDate get() = LocalDate.of(2025, 2, this)
/** 2025*/
val Int.mar25: LocalDate get() = LocalDate.of(2025, 3, this)
/** 2025*/
val Int.apr25: LocalDate get() = LocalDate.of(2025, 4, this)
/** 2025*/
val Int.jun25: LocalDate get() = LocalDate.of(2025, 6, this)

fun hashOppdrag(oppdrag: Oppdrag): Int { 
    return oppdragMapper.writeValueAsString(oppdrag).hashCode()
}

fun kvitterOk(oppdrag: Oppdrag): Oppdrag {
    return oppdragMapper.copy(oppdrag).apply {
        mmel = Mmel().apply { alvorlighetsgrad = "00" }
    }
}

var nextInt: Int = 0
    get() = field++

fun randomUtbetalingId(): UtbetalingId = UtbetalingId(UUID.randomUUID())

fun XMLGregorianCalendar.toLocalDate() = toGregorianCalendar().toZonedDateTime().toLocalDate()

fun MutableList<AapUtbetalingsdag>.meldekort(
    meldeperiode: String,
    fom: LocalDate, 
    tom: LocalDate,
    utbetaltBeløp: UInt,
    sats: UInt,
) {
    for(i in 0 ..< ChronoUnit.DAYS.between(fom, tom) + 1) {
        val dato = fom.plusDays(i)
        if (!dato.erHelg()) {
            add(AapUtbetalingsdag(meldeperiode, dato, sats, utbetaltBeløp))
        }
    }
}

object Aap {
    fun utbetaling(
        sakId: String = "$nextInt",
        behandlingId: String = "$nextInt",
        dryrun: Boolean = false,
        ident: String = "12345678910",
        vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
        utbetalinger: MutableList<AapUtbetalingsdag>.() -> Unit,
    ): AapUtbetaling = AapUtbetaling(
        dryrun = dryrun,
        behandlingId = behandlingId,
        sakId = sakId,
        ident = ident,
        vedtakstidspunktet = vedtakstidspunkt,
        utbetalinger = mutableListOf<AapUtbetalingsdag>().apply(utbetalinger),
    )

    fun meldekort(
        meldeperiode: String,
        fom: LocalDate,
        tom: LocalDate,
        sats: UInt,
        utbetaltBeløp: UInt = sats,
    ): List<AapUtbetalingsdag> {
        return buildList {
            for(i in 0 ..< ChronoUnit.DAYS.between(fom, tom) + 1) {
                val dato = fom.plusDays(i)
                if (!dato.erHelg()) {
                    add(AapUtbetalingsdag(meldeperiode, dato, sats, utbetaltBeløp))
                }
            }
        }
    }
}

object Dp {
    fun utbetaling(
        sakId: String = "$nextInt",
        behandlingId: String = "$nextInt",
        dryrun: Boolean = false,
        ident: String = "12345678910",
        vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
        utbetalinger: () -> List<DpUtbetalingsdag>,
    ): DpUtbetaling = DpUtbetaling(
        dryrun = dryrun,
        behandlingId = behandlingId,
        sakId = sakId,
        ident = ident,
        vedtakstidspunktet = vedtakstidspunkt,
        utbetalinger = utbetalinger(),
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
}

object Tp {
    fun utbetaling(
        sakId: String = "$nextInt",
        behandlingId: String = "$nextInt",
        dryrun: Boolean = false,
        personident: String = "12345678910",
        vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
        saksbehandler: String? = null,
        beslutter: String? = null,
        perioder: () -> List<TpPeriode>,
    ): TpUtbetaling = TpUtbetaling(
        sakId = sakId,
        behandlingId = behandlingId,
        dryrun = dryrun,
        personident = personident,
        vedtakstidspunkt = vedtakstidspunkt,
        perioder = perioder(),
        saksbehandler = saksbehandler,
        beslutter = beslutter,
    )

    fun periode(
        meldeperiode: String,
        fom: LocalDate,
        tom: LocalDate,
        beløp: UInt,
        betalendeEnhet: NavEnhet? = null,
        barnetillegg: Boolean = false,
        stønad: StønadTypeTiltakspenger = StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING,
        ): List<TpPeriode> {
        return listOf(TpPeriode(meldeperiode, fom, tom, betalendeEnhet, barnetillegg, beløp, stønad))
    }
}

object Ts {
    fun dto(
        sakId: String = "$nextInt",
        behandlingId: String = "$nextInt",
        dryrun: Boolean = false,
        ident: String = "12345678910",
        vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
        periodetype: Periodetype = Periodetype.EN_GANG,
        utbetalinger: () -> List<TsUtbetaling>,
    ): TsDto = TsDto(
        dryrun = dryrun,
        sakId = sakId,
        behandlingId = behandlingId,
        personident = ident,
        vedtakstidspunkt = vedtakstidspunkt,
        periodetype = periodetype,
        saksbehandler = null,
        beslutter = null,
        utbetalinger = utbetalinger(),
    )

    fun utbetaling(
        uid: UtbetalingId,
        brukFagområdeTillst: Boolean = true,
        stønad: StønadTypeTilleggsstønader = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
        perioder: () -> List<TsPeriode>,
    ): List<TsUtbetaling> = listOf(TsUtbetaling(
        id = uid.id,
        stønad = stønad,
        perioder = perioder(),
        brukFagområdeTillst = brukFagområdeTillst,
    ))

    fun periode(
        fom: LocalDate,
        tom: LocalDate,
        beløp: UInt,
    ): List<TsPeriode> {
        return listOf(TsPeriode(fom, tom, beløp))
    }
}

object Historisk {
    fun utbetaling(
        uid: UtbetalingId,
        sakId: String = "$nextInt",
        behandlingId: String = "$nextInt",
        dryrun: Boolean = false,
        ident: String = "12345678910",
        vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
        stønad: StønadTypeHistorisk = StønadTypeHistorisk.TILSKUDD_SMÅHJELPEMIDLER,
        periodetype: Periodetype = Periodetype.EN_GANG,
        utbetalinger: () -> List<HistoriskPeriode>,
    ): HistoriskUtbetaling = HistoriskUtbetaling(
        dryrun = dryrun, id = uid.id,
        stønad = stønad,
        behandlingId = behandlingId,
        sakId = sakId,
        personident = ident,
        vedtakstidspunkt = vedtakstidspunkt,
        periodetype = periodetype,
        perioder = utbetalinger(),
    )

    fun periode(
        fom: LocalDate,
        tom: LocalDate,
        beløp: UInt,
    ): List<HistoriskPeriode> {
        return listOf(HistoriskPeriode(fom, tom, beløp))
    }
}


fun utbetaling(
    action: Action,
    uid: UtbetalingId,
    sakId: SakId = SakId("$nextInt"),
    behandlingId: BehandlingId = BehandlingId("$nextInt"),
    originalKey: String = uid.id.toString(),
    førsteUtbetalingPåSak: Boolean = true,
    lastPeriodeId: PeriodeId = PeriodeId(),
    periodetype: Periodetype = Periodetype.UKEDAG,
    stønad: Stønadstype = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
    personident: Personident = Personident(""),
    vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
    beslutterId: Navident = Navident(""),
    saksbehandlerId: Navident = Navident(""),
    avvent: Avvent? = null,
    fagsystem: Fagsystem = Fagsystem.AAP,
    perioder: () -> List<Utbetalingsperiode> = { emptyList() },
) = Utbetaling(
    dryrun = false,
    uid = uid,
    originalKey = originalKey,
    action = action,
    førsteUtbetalingPåSak = førsteUtbetalingPåSak,
    periodetype = periodetype,
    stønad = stønad,
    sakId = sakId,
    behandlingId = behandlingId,
    lastPeriodeId = lastPeriodeId,
    personident = personident,
    vedtakstidspunkt = vedtakstidspunkt,
    beslutterId = beslutterId,
    saksbehandlerId = saksbehandlerId,
    avvent = avvent,
    fagsystem = fagsystem,
    perioder = perioder(),
)

fun periode(
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt = 123u,
    vedtakssats: UInt? = null,
    betalendeEnhet: NavEnhet? = null,
) = listOf(
    Utbetalingsperiode(
        fom = fom,
        tom = tom,
        beløp = beløp,
        vedtakssats = vedtakssats,
        betalendeEnhet = betalendeEnhet,
    )
)

fun utbetalingsperiode(
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt = 123u,
    vedtakssats: UInt? = null,
    betalendeEnhet: NavEnhet? = null,
) = Utbetalingsperiode(
    fom = fom,
    tom = tom,
    beløp = beløp,
    vedtakssats = vedtakssats,
    betalendeEnhet = betalendeEnhet,
)

val meldeperiode = "2025-08-01-2025-08-14"
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

