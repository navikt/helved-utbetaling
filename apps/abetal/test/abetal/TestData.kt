package abetal

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.xml.datatype.XMLGregorianCalendar
import models.*

val Int.jun21: LocalDate get() = LocalDate.of(2021, 6, this)
val Int.jul21: LocalDate get() = LocalDate.of(2021, 7, this)
val Int.aug21: LocalDate get() = LocalDate.of(2021, 8, this)
val Int.jun: LocalDate get() = LocalDate.of(2024, 6, this)
val Int.jul: LocalDate get() = LocalDate.of(2024, 7, this)
val Int.sep: LocalDate get() = LocalDate.of(2024, 9, this)
val Int.okt: LocalDate get() = LocalDate.of(2024, 10, this)
val Int.des: LocalDate get() = LocalDate.of(2024, 12, this)
val Int.jan: LocalDate get() = LocalDate.of(2025, 1, this)
val Int.feb25: LocalDate get() = LocalDate.of(2025, 2, this)
val Int.mar25: LocalDate get() = LocalDate.of(2025, 3, this)
val Int.apr25: LocalDate get() = LocalDate.of(2025, 4, this)

var nextInt: Int = 0
    get() = field++

fun randomUtbetalingId(): UtbetalingId = UtbetalingId(UUID.randomUUID())

fun XMLGregorianCalendar.toLocalDate() = toGregorianCalendar().toZonedDateTime().toLocalDate()

object Aap {
    fun utbetaling(
        sakId: String = "$nextInt",
        behandlingId: String = "$nextInt",
        dryrun: Boolean = false,
        ident: String = "12345678910",
        vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
        utbetalinger: () -> List<AapUtbetalingsdag>,
    ): AapUtbetaling = AapUtbetaling(
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

object Ts {
    fun utbetaling(
        uid: UtbetalingId,
        sakId: String = "$nextInt",
        behandlingId: String = "$nextInt",
        dryrun: Boolean = false,
        ident: String = "12345678910",
        vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
        brukFagområdeTillst: Boolean = true,
        stønad: StønadTypeTilleggsstønader = StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER,
        periodetype: Periodetype = Periodetype.EN_GANG,
        utbetalinger: () -> List<TsPeriode>,
    ): TsUtbetaling = TsUtbetaling(
        dryrun = dryrun,
        brukFagområdeTillst = brukFagområdeTillst,
        id = uid.id,
        stønad = stønad,
        behandlingId = behandlingId,
        sakId = sakId,
        personident= ident,
        vedtakstidspunkt = vedtakstidspunkt,
        periodetype = periodetype,
        perioder = utbetalinger(),
    )

    fun periode(
        fom: LocalDate,
        tom: LocalDate,
        beløp: UInt,
    ): List<TsPeriode> {
        return listOf(TsPeriode(fom, tom, beløp))
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
    stønad: Stønadstype = StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING, // TODO: Denen brukes i DP test?
    personident: Personident = Personident(""),
    vedtakstidspunkt: LocalDateTime = LocalDateTime.now(),
    beslutterId: Navident = Navident(""),
    saksbehandlerId: Navident = Navident(""),
    avvent: Avvent? = null,
    fagsystem: Fagsystem = Fagsystem.AAP,
    perioder: () -> List<Utbetalingsperiode>,
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
    vedtakssats: UInt? = beløp, // TODO: ingen eller null as default
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

