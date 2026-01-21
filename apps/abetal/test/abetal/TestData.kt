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

val Int.jun21: LocalDate get() = LocalDate.of(2021, 6, this)
val Int.jul21: LocalDate get() = LocalDate.of(2021, 7, this)
val Int.aug21: LocalDate get() = LocalDate.of(2021, 8, this)
val Int.jun: LocalDate get() = LocalDate.of(2024, 6, this)
val Int.jul: LocalDate get() = LocalDate.of(2024, 7, this)
val Int.aug: LocalDate get() = LocalDate.of(2024, 8, this)
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

object TestData {
    private val xmlMapper: libs.xml.XMLMapper<Oppdrag> = libs.xml.XMLMapper()
    private val objectFactory = ObjectFactory()
    private fun LocalDateTime.format() =
        truncatedTo(ChronoUnit.HOURS).format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS"))

    private fun LocalDate.toXMLDate(): XMLGregorianCalendar =
        DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar.from(atStartOfDay(ZoneId.systemDefault())))

    fun oppdrag(
        satser: List<Int>,
        oppdragslinjer: List<OppdragsLinje150> = satser.mapIndexed { idx, sats ->
            oppdragslinje(
                delytelsesId = "$idx",                              // periodeId
                sats = sats.toLong(),                               // beløp
                datoVedtakFom = LocalDate.of(2025, 11, 3),          // fom
                datoVedtakTom = LocalDate.of(2025, 11, 7),          // tom
                typeSats = "DAG",                                   // periodetype
                henvisning = UUID.randomUUID().toString().drop(10), // behandlingId
            )
        },
        kodeEndring: String = "NY",                                 // NY/ENDR
        fagområde: String = "AAP",
        fagsystemId: String = "1",                                  // sakid
        oppdragGjelderId: String = "12345678910",                   // personident 
        saksbehId: String = "Z999999",
        avstemmingstidspunkt: LocalDateTime = LocalDateTime.now(),
        enhet: String? = null,
        mmel: Mmel? = mmel(),
    ) = objectFactory.createOppdrag().apply {
        this.mmel = mmel
        this.oppdrag110 = objectFactory.createOppdrag110().apply {
            this.kodeAksjon = "1"
            this.kodeEndring = kodeEndring
            this.kodeFagomraade = fagområde
            this.fagsystemId = fagsystemId
            this.utbetFrekvens = "MND"
            this.oppdragGjelderId = oppdragGjelderId
            this.datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).toXMLDate()
            this.saksbehId = saksbehId
            this.avstemming115 = objectFactory.createAvstemming115().apply {
                kodeKomponent = fagområde
                nokkelAvstemming = avstemmingstidspunkt.format()
                tidspktMelding = avstemmingstidspunkt.format()
            }
            enhet?.let {
                listOf(
                    objectFactory.createOppdragsEnhet120().apply {
                        this.enhet = enhet
                        this.typeEnhet = "BOS"
                        this.datoEnhetFom = LocalDate.of(1970, 1, 1).toXMLDate()
                    },
                    objectFactory.createOppdragsEnhet120().apply {
                        this.enhet = "8020"
                        this.typeEnhet = "BEH"
                        this.datoEnhetFom = LocalDate.of(1900, 1, 1).toXMLDate()
                    },
                )
            } ?: listOf(
                objectFactory.createOppdragsEnhet120().apply {
                    this.enhet = "8020"
                    this.typeEnhet = "BOS"
                    this.datoEnhetFom = LocalDate.of(1900, 1, 1).toXMLDate()
                },
            ).forEach(oppdragsEnhet120s::add)
            oppdragsLinje150s.addAll(oppdragslinjer)
        }
    }

    fun mmel(
        alvorlighetsgrad: String = "00", 
        kodeMelding: String? = null,
        beskrMelding: String? = null,
    ): Mmel = Mmel().apply {
        this.alvorlighetsgrad = alvorlighetsgrad
        this.kodeMelding = kodeMelding
        this.beskrMelding = beskrMelding
    }

    fun oppdragslinje(
        delytelsesId: String,
        sats: Long,
        datoVedtakFom: LocalDate,
        datoVedtakTom: LocalDate,
        typeSats: String,                       // DAG/DAG7/MND/ENG
        henvisning: String,                     // behandlingId
        refDelytelsesId: String? = null,        // lastPeriodeId
        kodeEndring: String = "NY",             // NY/ENDR
        opphør: LocalDate? = null,
        fagsystemId: String = "1",              // sakId
        vedtakId: LocalDate = LocalDate.now(),  // vedtakstidspunkt
        klassekode: String = "AAPOR",
        saksbehId: String = "Z999999",          // saksbehandler
        beslutterId: String = "Z999999",        // beslutter
        utbetalesTilId: String = "12345678910", // personident
        vedtakssats: Long? = null,              // fastsattDagsats
    ): OppdragsLinje150 {
        val attestant = objectFactory.createAttestant180().apply {
            attestantId = beslutterId
        }

        return objectFactory.createOppdragsLinje150().apply {
            kodeEndringLinje = kodeEndring
            opphør?.let {
                kodeStatusLinje = TkodeStatusLinje.OPPH
                datoStatusFom = opphør.toXMLDate()
            }
            if (kodeEndring == "ENDR") {
                refDelytelsesId?.let {
                    refDelytelseId = refDelytelsesId
                    refFagsystemId = fagsystemId
                }
            }
            this.vedtakId = vedtakId.toString()
            this.delytelseId = delytelsesId
            this.kodeKlassifik = klassekode
            this.datoVedtakFom = datoVedtakFom.toXMLDate()
            this.datoVedtakTom = datoVedtakTom.toXMLDate()
            this.sats = BigDecimal.valueOf(sats)
            this.fradragTillegg = TfradragTillegg.T
            this.typeSats = typeSats
            this.brukKjoreplan = "N"
            this.saksbehId = saksbehId
            this.utbetalesTilId = utbetalesTilId
            this.henvisning = henvisning
            attestant180s.add(attestant)

            vedtakssats?.let {
                vedtakssats157 = objectFactory.createVedtakssats157().apply {
                    this.vedtakssats = BigDecimal.valueOf(vedtakssats)
                }
            }
        }
    }
}
