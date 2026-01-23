package urskog.oppdrag

import models.*
import no.trygdeetaten.skjema.oppdrag.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar

val Int.nov: LocalDate get() = LocalDate.of(2025, 11, this)

var seq: Int = 0
    get() = field++

object TestData {
    private val objectFactory = ObjectFactory()
    private fun LocalDateTime.format(pattern: String) = format(DateTimeFormatter.ofPattern(pattern))
    private val fixedTime = LocalTime.of(10, 10, 0, 0)

    fun ok(): Mmel = Mmel().apply {
        alvorlighetsgrad = "00"
    }

    fun oppdrag(
        oppdragslinjer: List<OppdragsLinje150>,
        kodeEndring: String = "NY",
        fagområde: String = "AAP", 
        fagsystemId: String = "1",
        oppdragGjelderId: String = "12345678910",
        saksbehId: String = "Z999999",
        avstemmingstidspunkt: LocalDateTime = LocalDateTime.now(),
        enhet: String? = null,
        mmel: Mmel? =  null,
    ): Oppdrag {
        return objectFactory.createOppdrag().apply {
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
                    val todayAtTen = LocalDateTime.now().with(fixedTime)
                    kodeKomponent = fagområde
                    nokkelAvstemming = todayAtTen.format("yyyy-MM-dd-HH.mm.ss.SSSSSS")
                    tidspktMelding = todayAtTen.format("yyyy-MM-dd-HH.mm.ss.SSSSSS")
                }
                oppdragsEnhet120(enhet).forEach(oppdragsEnhet120s::add)
                oppdragsLinje150s.addAll(oppdragslinjer)
            }
        }
    }

    fun oppdragslinje(
        delytelsesId: String,
        sats: Long,
        datoVedtakFom: LocalDate,
        datoVedtakTom: LocalDate,
        typeSats: String,                       // DAG/DAG7/MND/ENG
        henvisning: String,                     // behandlingId
        refDelytelsesId: String? = null,
        kodeEndring: String = "NY",             // NY /ENDR
        opphør: LocalDate? = null,
        fagsystemId: String = "1",              // sakid
        vedtakId: LocalDate = LocalDate.now(),  // vedtakstidspunkt
        klassekode: String = "AAPOR",
        saksbehId: String = "Z999999",
        beslutterId: String = "Z999999",
        utbetalesTilId: String = "12345678910", // personident
        vedtakssats: Long? = null,
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

    fun oppdragsEnhet120(enhet: String? = null): List<OppdragsEnhet120> {
        return enhet?.let {
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
        )
    }

    fun utbetaling(
        action: Action = Action.CREATE,
        uid: UtbetalingId,
        sakId: SakId = SakId("$seq"),
        behandlingId: BehandlingId = BehandlingId("$seq"),
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
}

private fun LocalDate.toXMLDate(): XMLGregorianCalendar =
    DatatypeFactory.newInstance()
        .newXMLGregorianCalendar(GregorianCalendar.from(atStartOfDay(ZoneId.systemDefault())))

