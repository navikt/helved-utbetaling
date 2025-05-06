package utsjekk.utbetaling

import TestData.random
import utsjekk.iverksetting.RandomOSURId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

val Int.jan: LocalDate get() = LocalDate.of(2025, 1, this)
val Int.feb: LocalDate get() = LocalDate.of(2024, 2, this)
val Int.mar: LocalDate get() = LocalDate.of(2024, 3, this)
val Int.aug: LocalDate get() = LocalDate.of(2024, 8, this)
val Int.des: LocalDate get() = LocalDate.of(2024, 12, this)

fun Int.jan(day: Int): LocalDate = LocalDate.of(this, 1, day)
fun Int.nov(day: Int): LocalDate = LocalDate.of(this, 11, day)

fun Personident.Companion.random(): Personident {
    return Personident(no.nav.utsjekk.kontrakter.felles.Personident.random().verdi)
}

fun UtbetalingId.Companion.random() = UtbetalingId(UUID.randomUUID())

fun UtbetalingsperiodeDto.Companion.opphør(
    from: Utbetaling,
    opphør: LocalDate,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
) = UtbetalingsperiodeDto.default(from, fom, tom, sats, klassekode, Satstype.VIRKEDAG, opphør = opphør)

fun UtbetalingsperiodeDto.Companion.default(
    from: Utbetaling,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
    satstype: Satstype = Satstype.MND,
    erEndringPåEsksisterendePeriode: Boolean = false,
    opphør: LocalDate? = null,
    periodeId: PeriodeId = PeriodeId(),
): UtbetalingsperiodeDto = UtbetalingsperiodeDto(
    erEndringPåEksisterendePeriode = erEndringPåEsksisterendePeriode,
    opphør = opphør?.let(::Opphør),
    vedtaksdato = from.vedtakstidspunkt.toLocalDate(),
    klassekode = klassekode,
    fom = fom,
    tom = tom,
    sats = sats,
    satstype = satstype,
    utbetalesTil = from.personident.ident,
    behandlingId = from.behandlingId.id,
    id = periodeId.toString(),
)

fun UtbetalingsperiodeDto.Companion.dag(
    from: Utbetaling,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
) = UtbetalingsperiodeDto.default(from, fom, tom, sats, klassekode, Satstype.VIRKEDAG)

fun UtbetalingsoppdragDto.Companion.dagpenger(
    uid: UtbetalingId,
    from: Utbetaling,
    perioder: List<UtbetalingsperiodeDto>,
    erFørsteUtbetalingPåSak: Boolean = true,
    fagsystem: FagsystemDto = FagsystemDto.DAGPENGER,
    avstemmingstidspunkt: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
    brukersNavKontor: String? = null,
): UtbetalingsoppdragDto = UtbetalingsoppdragDto(
    uid = uid,
    erFørsteUtbetalingPåSak = erFørsteUtbetalingPåSak,
    fagsystem = fagsystem,
    saksnummer = from.sakId.id,
    aktør = from.personident.ident,
    saksbehandlerId = from.saksbehandlerId.ident,
    beslutterId = from.beslutterId.ident,
    avstemmingstidspunkt = avstemmingstidspunkt,
    brukersNavKontor = brukersNavKontor,
    utbetalingsperioder = perioder,
)

fun UtbetalingApi.Companion.dagpenger(
    vedtakstidspunkt: LocalDate,
    periodeType: PeriodeType,
    perioder: List<UtbetalingsperiodeApi>,
    stønad: StønadTypeDagpenger = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
    sakId: SakId = SakId(RandomOSURId.generate()),
    personident: Personident = Personident.random(),
    behandlingId: BehandlingId = BehandlingId(RandomOSURId.generate()),
    saksbehandlerId: Navident = Navident(TestData.DEFAULT_SAKSBEHANDLER),
    beslutterId: Navident = Navident(TestData.DEFAULT_BESLUTTER),
    avvent: Avvent? = null,
): UtbetalingApi {
    return UtbetalingApi(
        sakId.id,
        behandlingId.id,
        personident.ident,
        vedtakstidspunkt.atStartOfDay(),
        stønad,
        beslutterId.ident,
        saksbehandlerId.ident,
        periodeType,
        perioder,
        avvent,
    )
}

fun Utbetalingsperiode.Companion.dagpenger(
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt,
    betalendeEnhet: NavEnhet? = null,
    fastsattDagpengesats: UInt? = null,
): Utbetalingsperiode = Utbetalingsperiode(
    fom,
    tom,
    beløp,
    betalendeEnhet,
    fastsattDagpengesats,
)

fun Utbetaling.Companion.dagpenger(
    vedtakstidspunkt: LocalDate,
    perioder: List<Utbetalingsperiode>,
    satstype: Satstype = Satstype.VIRKEDAG,
    lastPeriodeId: PeriodeId = PeriodeId(),
    stønad: StønadTypeDagpenger = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
    sakId: SakId = SakId(RandomOSURId.generate()),
    personident: Personident = Personident.random(),
    behandlingId: BehandlingId = BehandlingId(RandomOSURId.generate()),
    saksbehandlerId: Navident = Navident(TestData.DEFAULT_SAKSBEHANDLER),
    beslutterId: Navident = Navident(TestData.DEFAULT_BESLUTTER),
    avvent: Avvent? = null,
): Utbetaling = Utbetaling(
    sakId,
    behandlingId,
    lastPeriodeId,
    personident,
    vedtakstidspunkt.atStartOfDay(),
    stønad,
    beslutterId,
    saksbehandlerId,
    satstype,
    perioder,
    avvent,
)

