package abetal

import abetal.models.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.xml.datatype.XMLGregorianCalendar
import models.*

val Int.jan: LocalDate get() = LocalDate.of(2025, 1, this)
val Int.des: LocalDate get() = LocalDate.of(2024, 12, this)

var nextInt: Int = 0
    get() = field++

fun randomUtbetalingId(): UtbetalingId = UtbetalingId(UUID.randomUUID())

fun XMLGregorianCalendar.toLocalDate() = toGregorianCalendar().toZonedDateTime().toLocalDate()

object Aap {

    fun utbetaling(
        action: Action,
        sakId: SakId = SakId("$nextInt"),
        behId: BehandlingId = BehandlingId("$nextInt"),
        vedtatt: LocalDateTime = LocalDateTime.now(),
        perioder: () -> List<Utbetalingsperiode>,
    ) = AapUtbetaling(
        simulate = false,
        action = action,
        periodetype = Periodetype.UKEDAG,
        stønad = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
        sakId = sakId,
        behandlingId = behId,
        personident = Personident(""),
        vedtakstidspunkt = vedtatt,
        beslutterId = Navident(""),
        saksbehandlerId = Navident(""),
        perioder = perioder()
    )

    fun dag(
        dato: LocalDate,
        beløp: UInt = 123u,
        fastsattDagsats: UInt? = beløp,
    ) = Utbetalingsperiode(
        fom = dato,
        tom = dato,
        beløp = beløp,
        fastsattDagsats = fastsattDagsats,
    )
}
fun utbetaling(
    action: Action,
    uid: UtbetalingId,
    sakId: SakId = SakId("$nextInt"),
    behandlingId: BehandlingId = BehandlingId("$nextInt"),
    førsteUtbetalingPåSak: Boolean = true,
    lastPeriodeId: PeriodeId = PeriodeId(),
    periodetype: Periodetype = Periodetype.UKEDAG,
    stønad: Stønadstype = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING,
    personident: Personident = Personident(""),
    vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
    beslutterId: Navident = Navident(""),
    saksbehandlerId: Navident = Navident(""),
    perioder: () -> List<Utbetalingsperiode>,
) = Utbetaling(
    simulate = false,
    uid = uid,
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
    perioder = perioder(),
)

fun periode(
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt = 123u,
    fastsattDagsats: UInt? = beløp,
    betalendeEnhet: NavEnhet? = null,
) = Utbetalingsperiode(
    fom = fom,
    tom = tom,
    beløp = beløp,
    fastsattDagsats = fastsattDagsats,
    betalendeEnhet = betalendeEnhet,
)




