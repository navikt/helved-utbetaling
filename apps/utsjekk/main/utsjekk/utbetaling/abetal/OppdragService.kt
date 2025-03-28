package utsjekk.utbetaling.abetal

import no.trygdeetaten.skjema.oppdrag.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import models.nesteUkedag
import java.util.*
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar
import utsjekk.utbetaling.*

private val objectFactory = ObjectFactory()
private fun LocalDateTime.format() = truncatedTo(ChronoUnit.HOURS).format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS"))
private fun LocalDate.toXMLDate(): XMLGregorianCalendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar.from(atStartOfDay(ZoneId.systemDefault())))

object OppdragService {
    fun opprett(new: Utbetaling, erførsteUtbetalingPåSak: Boolean): Oppdrag {
        var forrigeId: PeriodeId? = null
        val oppdrag110 = objectFactory.createOppdrag110().apply {
            kodeAksjon = "1"
            kodeEndring = if(erførsteUtbetalingPåSak) "NY" else "ENDR"
            kodeFagomraade = FagsystemDto.from(new.stønad).kode
            fagsystemId = new.sakId.id
            utbetFrekvens = "MND"
            oppdragGjelderId = new.personident.ident
            datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).toXMLDate()
            saksbehId = new.saksbehandlerId.ident
            avstemming115 = objectFactory.createAvstemming115().apply {
                val avstemmingstidspunkt = LocalDateTime.now().format()
                nokkelAvstemming = avstemmingstidspunkt
                kodeKomponent = FagsystemDto.from(new.stønad).kode
                tidspktMelding = avstemmingstidspunkt
            }
            oppdragsEnhet120s.addAll(oppdragsEnhet120(new))
            new.perioder.mapIndexed { i, periode ->
                val periodeId = if (i == new.perioder.size - 1) new.lastPeriodeId else PeriodeId()
                val oppdragslinje = oppdragsLinje150(new, false, periode, periodeId, forrigeId.also { forrigeId = periodeId }, null)
                oppdragsLinje150s.add(oppdragslinje)
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
            kodeAksjon = "1"
            kodeEndring = "ENDR"
            kodeFagomraade = FagsystemDto.from(new.stønad).kode
            fagsystemId = new.sakId.id
            utbetFrekvens = "MND"
            oppdragGjelderId = new.personident.ident
            datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).toXMLDate()
            saksbehId = new.saksbehandlerId.ident
            avstemming115 = objectFactory.createAvstemming115().apply {
                val avstemmingstidspunkt = LocalDateTime.now().format()
                nokkelAvstemming = avstemmingstidspunkt
                kodeKomponent = FagsystemDto.from(new.stønad).name
                tidspktMelding = avstemmingstidspunkt
            }
            oppdragsEnhet120s.addAll(oppdragsEnhet120(new))
            val prev = prev.copy(perioder = prev.perioder.sortedBy { it.fom }) // assure its sorted
            val new = new.copy(perioder = new.perioder.sortedBy { it.fom }) // assure its sorted
            val opphørsdato = opphørsdato(new.perioder, prev.perioder, new.satstype)
            val opphørslinje = oppdragsLinje150(new, true, prev.perioder.last(), prev.lastPeriodeId, null, opphørsdato)
            if (opphørsdato != null) oppdragsLinje150s.add(opphørslinje)
            oppdragsLinje150s.addAll(nyeLinjer(new, prev, opphørsdato, false))
        }
        return objectFactory.createOppdrag().apply {
            this.oppdrag110 = oppdrag110
        }
    }

    // før denne kalles, join prev med status for å sjekke om den er locket (status != OK)
    fun delete(new: Utbetaling, prev: Utbetaling): Oppdrag {
        prev.validateLockedFields(new)

        val oppdrag110 = objectFactory.createOppdrag110().apply {
            kodeAksjon = "1"
            kodeEndring = "ENDR"
            kodeFagomraade = FagsystemDto.from(new.stønad).kode
            fagsystemId = new.sakId.id
            utbetFrekvens = "MND"
            oppdragGjelderId = new.personident.ident
            datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).toXMLDate()
            saksbehId = new.saksbehandlerId.ident
            avstemming115 = objectFactory.createAvstemming115().apply {
                val avstemmingstidspunkt = LocalDateTime.now().format()
                nokkelAvstemming = avstemmingstidspunkt
                kodeKomponent = FagsystemDto.from(new.stønad).kode
                tidspktMelding = avstemmingstidspunkt
            }
            oppdragsEnhet120s.addAll(oppdragsEnhet120(new))
            val sistePeriode = new.perioder.maxBy { it.fom }
            val opphør = new.perioder.minBy { it.fom }.fom
            val oppdragslinje = oppdragsLinje150(new, false, sistePeriode, prev.lastPeriodeId, null, opphør)
            oppdragsLinje150s.add(oppdragslinje)
        }
        return objectFactory.createOppdrag().apply {
            this.oppdrag110 = oppdrag110
        }
    }
}

fun opphørsdato(
    new: List<Utbetalingsperiode>,
    prev: List<Utbetalingsperiode>,
    satstype: Satstype,
): LocalDate? {
    if (new.first().fom > prev.first().fom) {
        // Forkorter fom i starten. Må opphøre fra start
        return prev.first().fom
    }

    // Hvis det finnes et mellomrom må vi opphøre fra starten av mellomrommet
    // TODO: respekter periodetype
    val opphørsdato = new
        .windowed(2)
        .find {
            if (satstype != Satstype.VIRKEDAG) {
                it.first().tom.plusDays(1) < it.last().fom
            } else {
                it.first().tom.nesteUkedag() < it.last().fom
            }
        }
        ?.first()?.tom?.plusDays(1) // kan sette nesteUkedag ved periodetype.ukedag hvis ønskelig

    // Hvis vi ikke har opphørsdato i et mellomrom kan det hende at den siste perioden i
    // ny utbetaling er kortere enn siste perioden i eksisterende utbetaling
    return opphørsdato ?: if (new.last().tom < prev.last().tom) new.last().tom.plusDays(1) else null
}

private fun nyeLinjer(
    new: Utbetaling,
    prev: Utbetaling,
    opphørsdato: LocalDate?,
    erFørsteUtbetalingPåSak: Boolean,
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
            oppdragsLinje150(new, false, p, pid, sistePeriodeId, null).also {
                sistePeriodeId = pid
            }
        }
}

private fun oppdragsLinje150(
    utbetaling: Utbetaling,
    erEndringPåEksisterendePeriode: Boolean,
    periode: Utbetalingsperiode,
    periodeId: PeriodeId,
    forrigePeriodeId: PeriodeId?,
    opphør: LocalDate?,
): OppdragsLinje150 {
    val attestant = objectFactory.createAttestant180().apply {
        attestantId = utbetaling.beslutterId.ident
    }
    return objectFactory.createOppdragsLinje150().apply {
        kodeEndringLinje = if (erEndringPåEksisterendePeriode) "ENDR" else "NY"
        opphør?.let {
            kodeStatusLinje = TkodeStatusLinje.OPPH
            datoStatusFom = it.toXMLDate()
        }
        forrigePeriodeId?.let { // TODO: må vi sjekke at denne er ENDR?
            refDelytelseId = forrigePeriodeId.toString()
            refFagsystemId = utbetaling.sakId.id
        }
        vedtakId = utbetaling.vedtakstidspunkt.toLocalDate().toString()
        delytelseId = periodeId.toString()
        kodeKlassifik = utbetaling.stønad.klassekode
        datoVedtakFom = periode.fom.toXMLDate()
        datoVedtakTom = periode.tom.toXMLDate()
        sats = BigDecimal.valueOf(periode.beløp.toLong())
        fradragTillegg = TfradragTillegg.T
        typeSats = utbetaling.satstype.kode
        brukKjoreplan = "N"
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

private fun oppdragsEnhet120(new: Utbetaling): List<OppdragsEnhet120> {
    val bos = objectFactory.createOppdragsEnhet120().apply {
        enhet = new.perioder.betalendeEnhet()?.enhet ?: "8020"
        typeEnhet = "BOS"
        datoEnhetFom = LocalDate.of(1970, 1, 1).toXMLDate()
    }
    val beh = objectFactory.createOppdragsEnhet120().apply {
        enhet = "8020"
        typeEnhet = "BEH"
        datoEnhetFom = LocalDate.of(1970, 1, 1).toXMLDate()
    }
    return when (new.perioder.betalendeEnhet()) {
        null -> listOf(bos)
        else -> listOf(bos, beh)
    }
}
