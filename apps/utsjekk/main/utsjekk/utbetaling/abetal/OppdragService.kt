package utsjekk.utbetaling.abetal

import models.nesteUkedag
import no.trygdeetaten.skjema.oppdrag.*
import utsjekk.utbetaling.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar

private val objectFactory = ObjectFactory()
private fun LocalDateTime.format(pattern: String) = format(DateTimeFormatter.ofPattern(pattern))
private fun LocalDate.toXMLDate(): XMLGregorianCalendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar.from(atStartOfDay(ZoneId.systemDefault())))

private fun FagsystemDto.utbetalingFrekvens() = when(this) {
    FagsystemDto.HISTORISK -> "ENG"
    else -> "MND"
}

private fun FagsystemDto.kodekomponent() = when(this) {
    FagsystemDto.HISTORISK -> "INFO"
    else -> this.kode
}

object OppdragService {
    fun opprett(new: Utbetaling, erførsteUtbetalingPåSak: Boolean): Oppdrag {
        val fagsystemDto = FagsystemDto.from(new.stønad)
        val oppdrag110 = objectFactory.createOppdrag110().apply {
            kodeAksjon = "1"
            kodeEndring = if (erførsteUtbetalingPåSak) "NY" else "ENDR"
            kodeFagomraade = fagsystemDto.kode
            fagsystemId = new.sakId.id
            utbetFrekvens = fagsystemDto.utbetalingFrekvens()
            oppdragGjelderId = new.personident.ident
            datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).toXMLDate()
            saksbehId = new.saksbehandlerId.ident
            avstemming115 = avstemming115(fagsystemDto.kodekomponent())
            new.avvent?.let { avvent118 = avvent118(it) }
            oppdragsEnhet120s.addAll(oppdragsEnhet120(new))
            addLinjer(new)
        }
        return objectFactory.createOppdrag().apply {
            this.oppdrag110 = oppdrag110
        }
    }

    private fun Oppdrag110.addLinjer(utbetaling: Utbetaling) {
        var forrigeId: PeriodeId? = null
        utbetaling.perioder.mapIndexed { i, periode ->
            val periodeId = if (i == utbetaling.perioder.size - 1) utbetaling.lastPeriodeId else PeriodeId()
            val oppdragslinje =
                oppdragsLinje150(
                    utbetaling = utbetaling,
                    erEndringPåEksisterendePeriode = false,
                    periode = periode,
                    periodeId = periodeId,
                    forrigePeriodeId = forrigeId.also { forrigeId = periodeId },
                    opphør = null
                )
            oppdragsLinje150s.add(oppdragslinje)
        }
    }

    // før denne kalles, join prev med status for å sjekke om den er locket (status != OK)
    fun update(new: Utbetaling, prev: Utbetaling): Oppdrag {
        prev.validateLockedFields(new)
        prev.validateMinimumChanges(new)
        val fagsystemDto = FagsystemDto.from(new.stønad)

        val oppdrag110 = objectFactory.createOppdrag110().apply {
            kodeAksjon = "1"
            kodeEndring = "ENDR"
            kodeFagomraade = fagsystemDto.kode
            fagsystemId = new.sakId.id
            utbetFrekvens = fagsystemDto.utbetalingFrekvens()
            oppdragGjelderId = new.personident.ident
            datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).toXMLDate()
            saksbehId = new.saksbehandlerId.ident
            avstemming115 = avstemming115(fagsystemDto.kodekomponent())
            new.avvent?.let { avvent118 = avvent118(it) }
            oppdragsEnhet120s.addAll(oppdragsEnhet120(new))
            val prev = prev.copy(perioder = prev.perioder.sortedBy { it.fom }) // assure its sorted
            val new = new.copy(perioder = new.perioder.sortedBy { it.fom }) // assure its sorted
            val opphørsdato = opphørsdato(new.perioder, prev.perioder, new.satstype)
            val opphørslinje = oppdragsLinje150(new, true, prev.perioder.last(), prev.lastPeriodeId, null, opphørsdato)
            if (opphørsdato != null) oppdragsLinje150s.add(opphørslinje)
            oppdragsLinje150s.addAll(nyeLinjer(new, prev, opphørsdato))
        }
        return objectFactory.createOppdrag().apply {
            this.oppdrag110 = oppdrag110
        }
    }

    // før denne kalles, join prev med status for å sjekke om den er locket (status != OK)
    fun delete(new: Utbetaling, prev: Utbetaling): Oppdrag {
        prev.validateLockedFields(new)
        prev.validateEqualityOnDelete(new)
        val fagsystemDto = FagsystemDto.from(new.stønad)

        val oppdrag110 = objectFactory.createOppdrag110().apply {
            kodeAksjon = "1"
            kodeEndring = "ENDR"
            kodeFagomraade = fagsystemDto.kode
            fagsystemId = new.sakId.id
            utbetFrekvens = fagsystemDto.utbetalingFrekvens()
            oppdragGjelderId = new.personident.ident
            datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).toXMLDate()
            saksbehId = new.saksbehandlerId.ident
            avstemming115 = avstemming115(fagsystemDto.kodekomponent()) 
            new.avvent?.let { avvent118 = avvent118(it) }
            oppdragsEnhet120s.addAll(oppdragsEnhet120(new))
            val sistePeriode = new.perioder.maxBy { it.fom }
            val opphør = new.perioder.minBy { it.fom }.fom
            val oppdragslinje = oppdragsLinje150(new, false, sistePeriode, PeriodeId(), prev.lastPeriodeId, opphør)
            oppdragsLinje150s.add(oppdragslinje)
        }
        return objectFactory.createOppdrag().apply {
            this.oppdrag110 = oppdrag110
        }
    }
}

private fun avstemming115(fagsystemKode: String): Avstemming115 {
    return objectFactory.createAvstemming115().apply {
        kodeKomponent = fagsystemKode
        nokkelAvstemming = LocalDateTime.now().format("yyyy-MM-dd-HH.mm.ss.SSSSSS")
        tidspktMelding = LocalDateTime.now().format("yyyy-MM-dd-HH.mm.ss.SSSSSS")
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
        forrigePeriodeId?.let {
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

private fun avvent118(avvent: Avvent): Avvent118 {
    return objectFactory.createAvvent118().apply {
        datoAvventFom = avvent.fom.toXMLDate()
        datoAvventTom = avvent.tom.toXMLDate()
        datoOverfores = avvent.overføres.toXMLDate()
        avvent.årsak?.let { årsak -> kodeArsak = årsak.kode }
        feilreg = if (avvent.feilregistrering) "J" else "N"
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
