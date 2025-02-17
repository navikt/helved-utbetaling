package abetal.models

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.GregorianCalendar
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar
import no.trygdeetaten.skjema.oppdrag.*

enum class Fagsystem {
    DP,
    TILTPENG,
    TILLST,
    AAP;

    companion object {
        fun from(stønad: Stønadstype) = when (stønad) {
            is StønadTypeDagpenger -> Fagsystem.DP
            is StønadTypeTiltakspenger -> Fagsystem.TILTPENG
            is StønadTypeTilleggsstønader -> Fagsystem.TILLST
            is StønadTypeAAP -> Fagsystem.AAP
        }
    }
}

enum class Endringskode {
    NY,
    ENDR,
}

enum class Kvitteringstatus(val kode: String) {
    OK("00"),
    MED_MANGLER("04"), // MED INFORMASJON
    FUNKSJONELL_FEIL("08"),
    TEKNISK_FEIL("12"),
    UKJENT("Ukjent");
}

enum class Utbetalingsfrekvens(val kode: String) {
    DAGLIG("DAG"),
    UKENTLIG("UKE"),
    MÅNEDLIG("MND"),
    DAGLIG_14("14DG"),
    ENGANGSUTBETALING("ENG"),
}

object OppdragSkjemaConstants {
    val OPPDRAG_GJELDER_DATO_FOM: LocalDate = LocalDate.of(2000, 1, 1)
    val BRUKERS_NAVKONTOR_FOM: LocalDate = LocalDate.of(1970, 1, 1)
    val ENHET_FOM: LocalDate = LocalDate.of(1900, 1, 1)
    val FRADRAG_TILLEGG = TfradragTillegg.T
    const val KODE_AKSJON = "1"
    const val ENHET_TYPE_BOSTEDSENHET = "BOS"
    const val ENHET_TYPE_BEHANDLENDE_ENHET = "BEH"
    const val ENHET = "8020"
    const val BRUK_KJØREPLAN_DEFAULT = "N"
}

private val objectFactory = ObjectFactory()
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")

object OppdragService {
    fun opprett(new: Utbetaling): Oppdrag {
        var forrigeId: PeriodeId? = null
        val oppdrag110 = objectFactory.createOppdrag110().apply { 
            kodeAksjon = OppdragSkjemaConstants.KODE_AKSJON
            kodeEndring = if(new.førsteUtbetalingPåSak) Endringskode.NY.name else Endringskode.ENDR.name
            kodeFagomraade = Fagsystem.from(new.stønad).name
            fagsystemId = new.sakId.id
            utbetFrekvens = Utbetalingsfrekvens.MÅNEDLIG.kode
            oppdragGjelderId = new.personident.ident
            datoOppdragGjelderFom = OppdragSkjemaConstants.OPPDRAG_GJELDER_DATO_FOM.toXMLDate()
            saksbehId = new.saksbehandlerId.ident
            avstemming115 = objectFactory.createAvstemming115().apply {
                val avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).format(timeFormatter)
                nokkelAvstemming = avstemmingstidspunkt
                kodeKomponent = Fagsystem.from(new.stønad).name
                tidspktMelding = avstemmingstidspunkt 
            }
            oppdragsEnhet120(new).forEach(oppdragsEnhet120s::add)
            new.perioder.mapIndexed { i, periode ->
                val periodeId = if(i == new.perioder.size - 1) new.lastPeriodeId else PeriodeId()
                oppdragsLinje150s.add(
                    oppdragsLinje150(
                        periodeId = periodeId,
                        erEndringPåEksisterendePeriode = false,
                        forrigePeriodeId = forrigeId.also { forrigeId = periodeId },
                        periode = periode,
                        opphør = null,
                        utbetaling = new,
                    ),
                )
            }
        }

        return objectFactory.createOppdrag().apply {
            this.oppdrag110 = oppdrag110
        }
    }

    // før denne kalles, join prev med status for å sjekke om den er locket (status != OK)
    fun update(new: Utbetaling, prev: Utbetaling): Oppdrag {
        prev.validateLockedFields(new)
        prev.validateMinimumChanges(new)

        val oppdrag110 = objectFactory.createOppdrag110().apply {
            kodeAksjon = OppdragSkjemaConstants.KODE_AKSJON
            kodeEndring = Endringskode.ENDR.name
            kodeFagomraade = Fagsystem.from(new.stønad).name
            fagsystemId = new.sakId.id
            utbetFrekvens = Utbetalingsfrekvens.MÅNEDLIG.kode
            oppdragGjelderId = new.personident.ident
            datoOppdragGjelderFom = OppdragSkjemaConstants.OPPDRAG_GJELDER_DATO_FOM.toXMLDate()
            saksbehId = new.saksbehandlerId.ident
            avstemming115 = objectFactory.createAvstemming115().apply {
                val avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).format(timeFormatter)
                nokkelAvstemming = avstemmingstidspunkt
                kodeKomponent = Fagsystem.from(new.stønad).name
                tidspktMelding = avstemmingstidspunkt 
            }
            oppdragsEnhet120(new).forEach(oppdragsEnhet120s::add)

            val prev = prev.copy(perioder = prev.perioder.sortedBy { it.fom }) // assure its sorted
            val new = new.copy(perioder = new.perioder.sortedBy { it.fom }) // assure its sorted
            val opphørsdato = opphørsdato(prev.perioder, new.perioder)
            if (opphørsdato != null) oppdragsLinje150s.add(opphørslinje(new, prev.perioder.last(), prev.lastPeriodeId, opphørsdato))
            oppdragsLinje150s.addAll(nyeLinjer(new, prev, opphørsdato))
        }

        return objectFactory.createOppdrag().apply {
            this.oppdrag110 = oppdrag110
        }
    }

    // før denne kalles, join prev med status for å sjekke om den er locket (status != OK)
    fun delete(new: Utbetaling, prev: Utbetaling): Oppdrag {
        prev.validateLockedFields(new)

        val oppdrag110 = objectFactory.createOppdrag110().apply {
            kodeAksjon = OppdragSkjemaConstants.KODE_AKSJON
            kodeEndring = Endringskode.ENDR.name
            kodeFagomraade = Fagsystem.from(new.stønad).name
            fagsystemId = new.sakId.id
            utbetFrekvens = Utbetalingsfrekvens.MÅNEDLIG.kode
            oppdragGjelderId = new.personident.ident
            datoOppdragGjelderFom = OppdragSkjemaConstants.OPPDRAG_GJELDER_DATO_FOM.toXMLDate()
            saksbehId = new.saksbehandlerId.ident
            avstemming115 = objectFactory.createAvstemming115().apply {
                val avstemmingstidspunkt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).format(timeFormatter)
                nokkelAvstemming = avstemmingstidspunkt
                kodeKomponent = Fagsystem.from(new.stønad).name
                tidspktMelding = avstemmingstidspunkt 
            }
            oppdragsEnhet120(new).forEach(oppdragsEnhet120s::add)
            val sistePeriode = new.perioder.maxBy { it.fom }
            oppdragsLinje150s.add(
                oppdragsLinje150(
                    periodeId = prev.lastPeriodeId,
                    erEndringPåEksisterendePeriode = true,
                    forrigePeriodeId = null,
                    periode = sistePeriode,
                    opphør = new.perioder.minBy { it.fom }.fom,
                    utbetaling = new,
                ),
            )
        }

        return objectFactory.createOppdrag().apply {
            this.oppdrag110 = oppdrag110
        }
    }
}

private fun opphørsdato(
    new: List<Utbetalingsperiode>,
    prev: List<Utbetalingsperiode>,
): LocalDate? {
    if (new.first().fom > prev.first().fom) {
        // Forkorter fom i starten. Må opphøre fra start
        return prev.first().fom
    }

    // Hvis det finnes et mellomrom må vi opphøre fra starten av mellomrommet
    val opphørsdato = new
        .windowed(2)
        .find { it.first().tom < it.last().fom.minusDays(1) }
        ?.first()?.tom?.plusDays(1)

    // Hvis vi ikke har opphørsdato i et mellomrom kan det hende at den siste perioden i
    // ny utbetaling er kortere enn siste perioden i eksisterende utbetaling
        return opphørsdato ?: if (new.last().tom < prev.last().tom) new.last().tom.plusDays(1) else null
}

private fun nyeLinjer(
    new: Utbetaling,
    prev: Utbetaling,
    opphørsdato: LocalDate?,
): List<OppdragsLinje150> {
    var førsteEndringIdx = prev.perioder.zip(new.perioder).indexOfFirst { it.first != it.second }

    when {
        førsteEndringIdx == -1 && new.perioder.size > prev.perioder.size -> { 
            // De(n) nye endringen(e) kommer etter siste eksisterende periode.
            førsteEndringIdx = prev.perioder.size
        }
        førsteEndringIdx == -1 -> {
            return emptyList()
        }
        else -> {
            // Om første endring er en forkorting av tom ønsker vi ikke sende med denne som en ny utbetalingslinje.
            // Opphørslinjen tar ansvar for forkortingen av perioden, og vi ønsker bare å sende med alt etter perioden
            // som har endret seg.
            if (prev.perioder[førsteEndringIdx].tom > new.perioder[førsteEndringIdx].tom
                && prev.perioder[førsteEndringIdx].beløp == new.perioder[førsteEndringIdx].beløp
                && prev.perioder[førsteEndringIdx].fom == new.perioder[førsteEndringIdx].fom
            ) {
                førsteEndringIdx += 1
            }
        }
    }

    var sistePeriodeId = prev.lastPeriodeId
    return new.perioder
        .slice(førsteEndringIdx until new.perioder.size)
        .filter { if (opphørsdato != null) it.fom >= opphørsdato else true }
        .map { p ->
            val pid = PeriodeId()
            oppdragsLinje150(
                periodeId = pid,
                erEndringPåEksisterendePeriode = false,
                forrigePeriodeId = sistePeriodeId,
                periode = p,
                opphør = null,
                utbetaling = new,
            ).also {
                sistePeriodeId = pid
            }
        }
}

private fun opphørslinje(
    new: Utbetaling,
    sistePeriode: Utbetalingsperiode,
    periodeId: PeriodeId,
    opphørsdato: LocalDate,
): OppdragsLinje150 = oppdragsLinje150(
    periodeId = periodeId,
    erEndringPåEksisterendePeriode = true,
    forrigePeriodeId = null,
    periode = sistePeriode,
    opphør = opphørsdato,
    utbetaling = new,
)

private fun oppdragsEnhet120(utbetaling: Utbetaling): List<OppdragsEnhet120> {
    return utbetaling.perioder.betalendeEnhet()?.let { betalendeEnhet ->
        listOf(
            objectFactory.createOppdragsEnhet120().apply {
                enhet = betalendeEnhet.enhet
                typeEnhet = OppdragSkjemaConstants.ENHET_TYPE_BOSTEDSENHET
                datoEnhetFom = OppdragSkjemaConstants.BRUKERS_NAVKONTOR_FOM.toXMLDate()
            },
            objectFactory.createOppdragsEnhet120().apply {
                enhet = OppdragSkjemaConstants.ENHET
                typeEnhet = OppdragSkjemaConstants.ENHET_TYPE_BEHANDLENDE_ENHET
                datoEnhetFom = OppdragSkjemaConstants.ENHET_FOM.toXMLDate()
            },
        )
    } ?: listOf(
        objectFactory.createOppdragsEnhet120().apply {
            enhet = OppdragSkjemaConstants.ENHET
            typeEnhet = OppdragSkjemaConstants.ENHET_TYPE_BOSTEDSENHET
            datoEnhetFom = OppdragSkjemaConstants.ENHET_FOM.toXMLDate()
        },
    )
}

private fun oppdragsLinje150(
    periodeId: PeriodeId,
    erEndringPåEksisterendePeriode: Boolean,
    forrigePeriodeId: PeriodeId?,
    periode: Utbetalingsperiode,
    opphør: LocalDate?,
    utbetaling: Utbetaling,
): OppdragsLinje150 {
    val attestant = objectFactory.createAttestant180().apply {
        attestantId = utbetaling.beslutterId.ident
    }

    return objectFactory.createOppdragsLinje150().apply {
        kodeEndringLinje = if (erEndringPåEksisterendePeriode) Endringskode.ENDR.name else Endringskode.NY.name
        opphør?.let {
            kodeStatusLinje = TkodeStatusLinje.OPPH
            datoStatusFom = it.toXMLDate()
        }
        if (!erEndringPåEksisterendePeriode) {
            forrigePeriodeId?.let {
                refDelytelseId = it.toString()
                refFagsystemId = utbetaling.sakId.id
            }
        }
        vedtakId = utbetaling.vedtakstidspunkt.toLocalDate().toString()
        delytelseId = periodeId.toString()
        kodeKlassifik = utbetaling.stønad.klassekode
        datoVedtakFom = periode.fom.toXMLDate()
        datoVedtakTom = periode.tom.toXMLDate()
        sats = BigDecimal.valueOf(periode.beløp.toLong())
        fradragTillegg = OppdragSkjemaConstants.FRADRAG_TILLEGG
        typeSats = utbetaling.periodetype.satstype
        brukKjoreplan = OppdragSkjemaConstants.BRUK_KJØREPLAN_DEFAULT
        saksbehId = utbetaling.saksbehandlerId.ident
        utbetalesTilId = utbetaling.personident.ident
        henvisning = utbetaling.behandlingId.id
        attestant180s.add(attestant)

        periode.fastsattDagsats?.let { fastsattDagsats ->
            vedtakssats157 = objectFactory.createVedtakssats157().apply {
                vedtakssats = BigDecimal.valueOf(fastsattDagsats.toLong())
            }
        }
    }
}

val Oppdrag.kvitteringstatus: Kvitteringstatus
    get() = when (getMmel()?.alvorlighetsgrad) {
        "00" -> Kvitteringstatus.OK
        "04" -> Kvitteringstatus.MED_MANGLER
        "08" -> Kvitteringstatus.FUNKSJONELL_FEIL
        "12" -> Kvitteringstatus.TEKNISK_FEIL
        else -> Kvitteringstatus.UKJENT
    }

internal fun LocalDate.toXMLDate(): XMLGregorianCalendar =
    DatatypeFactory.newInstance()
        .newXMLGregorianCalendar(GregorianCalendar.from(atStartOfDay(ZoneId.systemDefault())))
