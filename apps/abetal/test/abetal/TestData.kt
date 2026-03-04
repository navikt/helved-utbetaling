package abetal

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.trygdeetaten.skjema.oppdrag.*
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
/** 2025 */
val Int.nov: LocalDate get() = LocalDate.of(2025, 11, this)
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
/** 2025*/
val Int.des25: LocalDate get() = LocalDate.of(2025, 12, this)

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

fun periode(
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt,
    vedtakssats: UInt? = null,
    betalendeEnhet: NavEnhet? = null,
): Utbetalingsperiode {
    return Utbetalingsperiode(
        fom = fom,
        tom = tom,
        beløp = beløp,
        vedtakssats = vedtakssats,
        betalendeEnhet = betalendeEnhet,
    )
}

fun MutableList<Utbetalingsperiode>.periode(
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt,
    vedtakssats: UInt? = null,
    betalendeEnhet: NavEnhet? = null,
) {
    add(Utbetalingsperiode(
        fom = fom,
        tom = tom,
        beløp = beløp,
        vedtakssats = vedtakssats,
        betalendeEnhet = betalendeEnhet,
    ))
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
    perioder: MutableList<Utbetalingsperiode>.() -> Unit,
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
    perioder = mutableListOf<Utbetalingsperiode>().apply(perioder),
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
