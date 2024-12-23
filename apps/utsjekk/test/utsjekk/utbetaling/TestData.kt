package utsjekk.utbetaling

import TestData.random
import utsjekk.iverksetting.RandomOSURId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import utsjekk.avstemming.nesteVirkedag
import utsjekk.avstemming.erHelligdag

val Int.jan: LocalDate get() = LocalDate.of(2025, 3, this)
val Int.feb: LocalDate get() = LocalDate.of(2024, 2, this)
val Int.mar: LocalDate get() = LocalDate.of(2024, 3, this)
val Int.aug: LocalDate get() = LocalDate.of(2024, 8, this)
val Int.des: LocalDate get() = LocalDate.of(2024, 12, this)
val virkedager: (LocalDate) -> LocalDate = { it.nesteVirkedag() }
val alleDager: (LocalDate) -> LocalDate = { it.plusDays(1) }

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
) = UtbetalingsperiodeDto.default(from, fom, tom, sats, klassekode, Satstype.DAG, opphør = opphør)

fun UtbetalingsperiodeDto.Companion.default(
    from: Utbetaling,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
    satstype: Satstype = Satstype.MND,
    erEndringPåEsksisterendePeriode: Boolean = false,
    opphør: LocalDate? = null,
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
    id = 1u, // TODO: denne øker per utbetaling for en sak
)

fun UtbetalingsperiodeDto.Companion.dag(
    from: Utbetaling,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
) = UtbetalingsperiodeDto.default(from, fom, tom, sats, klassekode, Satstype.DAG)

fun UtbetalingsperiodeDto.Companion.virkedag(
    from: Utbetaling,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
) = UtbetalingsperiodeDto.default(from, fom, tom, sats, klassekode, Satstype.VIRKEDAG)

fun UtbetalingsperiodeDto.Companion.mnd(
    from: Utbetaling,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
) = UtbetalingsperiodeDto.default(from, fom, tom, sats, klassekode, Satstype.MND)

fun UtbetalingsperiodeDto.Companion.eng(
    from: Utbetaling,
    fom: LocalDate,
    tom: LocalDate,
    sats: UInt,
    klassekode: String,
) = UtbetalingsperiodeDto.default(from, fom, tom, sats, klassekode, Satstype.ENGANGS)

fun UtbetalingsoppdragDto.Companion.dagpenger(
    uid: UtbetalingId, 
    from: Utbetaling,
    periode: UtbetalingsperiodeDto,
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
    utbetalingsperiode = periode,
)

fun UtbetalingApi.Companion.dagpenger(
    vedtakstidspunkt: LocalDate,
    perioder: List<UtbetalingsperiodeApi>,
    stønad: StønadTypeDagpenger = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
    sakId: SakId = SakId(RandomOSURId.generate()),
    personident: Personident = Personident.random(),
    behandlingId: BehandlingId = BehandlingId(RandomOSURId.generate()),
    saksbehandlerId: Navident = Navident(TestData.DEFAULT_SAKSBEHANDLER),
    beslutterId: Navident = Navident(TestData.DEFAULT_BESLUTTER),
): UtbetalingApi {
    return UtbetalingApi(
        sakId.id,
        behandlingId.id,
        personident.ident,
        vedtakstidspunkt.atStartOfDay(),
        stønad,
        beslutterId.ident,
        saksbehandlerId.ident,
        perioder,
    )
}

fun UtbetalingsperiodeApi.Companion.expand(
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt,
    expansionStrategy: (LocalDate) -> LocalDate,
    betalendeEnhet: NavEnhet? = null,
    fastsattDagpengesats: UInt? = null,
): List<UtbetalingsperiodeApi> = buildList {
    var date = fom
    while (date.isBefore(tom) || date.isEqual(tom)) {
        add(UtbetalingsperiodeApi(date, date, beløp, betalendeEnhet?.enhet, fastsattDagpengesats))
        date = expansionStrategy(date)
    }
}

fun Utbetalingsperiode.Companion.dagpenger(
    fom: LocalDate,
    tom: LocalDate,
    beløp: UInt,
    satstype: Satstype,
    betalendeEnhet: NavEnhet? = null,
    fastsattDagpengesats: UInt? = null,
    id: UInt = 1u, // denne øker med +1 for hver utbetaling på en sakId 
): Utbetalingsperiode = Utbetalingsperiode(
    id,
    fom,
    tom,
    beløp,
    satstype,
    betalendeEnhet,
    fastsattDagpengesats,
)

fun Utbetaling.Companion.dagpenger(
    vedtakstidspunkt: LocalDate,
    periode: Utbetalingsperiode,
    stønad: StønadTypeDagpenger = StønadTypeDagpenger.ARBEIDSSØKER_ORDINÆR,
    sakId: SakId = SakId(RandomOSURId.generate()),
    personident: Personident = Personident.random(),
    behandlingId: BehandlingId = BehandlingId(RandomOSURId.generate()),
    saksbehandlerId: Navident = Navident(TestData.DEFAULT_SAKSBEHANDLER),
    beslutterId: Navident = Navident(TestData.DEFAULT_BESLUTTER),
): Utbetaling = Utbetaling(
    sakId,
    behandlingId,
    personident,
    vedtakstidspunkt.atStartOfDay(),
    stønad,
    beslutterId,
    saksbehandlerId,
    periode,
)

