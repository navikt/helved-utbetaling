package abetal

import models.*
import no.trygdeetaten.skjema.oppdrag.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar

private val objectFactory = ObjectFactory()
private fun LocalDateTime.format(pattern: String) = format(DateTimeFormatter.ofPattern(pattern))
private val fixedTime = LocalTime.of(10, 10, 0, 0)
fun LocalDate.toXMLDate(): XMLGregorianCalendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar.from(atStartOfDay(ZoneId.systemDefault())))

object OppdragService {
    fun opprett(new: Utbetaling): Oppdrag {
        var forrigeId: PeriodeId? = null
        val oppdrag110 = objectFactory.createOppdrag110().apply {
            kodeAksjon = "1"
            kodeEndring = if(new.førsteUtbetalingPåSak) "NY" else "ENDR"
            kodeFagomraade = new.fagsystem.fagområde
            fagsystemId = new.sakId.id
            utbetFrekvens = "MND"
            oppdragGjelderId = new.personident.ident
            datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).toXMLDate()
            saksbehId = new.saksbehandlerId.ident
            avstemming115 = avstemming115(new.fagsystem.fagområde) 
            new.avvent?.let { avvent118 = avvent118(it) }
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

    fun update(new: Utbetaling, prev: Utbetaling): Oppdrag {
        prev.validateLockedFields(new)
        prev.validateMinimumChanges(new)

        val oppdrag110 = objectFactory.createOppdrag110().apply {
            kodeAksjon = "1"
            kodeEndring = "ENDR"
            kodeFagomraade = new.fagsystem.fagområde
            fagsystemId = new.sakId.id
            utbetFrekvens = "MND"
            oppdragGjelderId = new.personident.ident
            datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).toXMLDate()
            saksbehId = new.saksbehandlerId.ident
            avstemming115 = avstemming115(new.fagsystem.fagområde) 
            new.avvent?.let { avvent118 = avvent118(it) }
            oppdragsEnhet120s.addAll(oppdragsEnhet120(new))
            val prev = prev.copy(perioder = prev.perioder.sortedBy { it.fom }) // assure its sorted
            val new = new.copy(perioder = new.perioder.sortedBy { it.fom }) // assure its sorted
            val nyeLinjer = nyeLinjer(new, prev)
            val opphørsdato = opphørsdato(new.perioder, prev.perioder)

            if (skalTilføreOpphørslinje(opphørsdato, nyeLinjer)) {
                val opphørslinje = oppdragsLinje150(new, true, prev.perioder.last(), prev.lastPeriodeId, null, opphørsdato)
                oppdragsLinje150s.add(opphørslinje)
            }

            oppdragsLinje150s.addAll(nyeLinjer)
        }
        return objectFactory.createOppdrag().apply {
            this.oppdrag110 = oppdrag110
        }
    }

    // TODO: Trenger vi både new og prev?
    fun delete(new: Utbetaling, prev: Utbetaling): Oppdrag {
        prev.validateLockedFields(new)

        val oppdrag110 = objectFactory.createOppdrag110().apply {
            kodeAksjon = "1"
            kodeEndring = "ENDR"
            kodeFagomraade = new.fagsystem.fagområde
            fagsystemId = new.sakId.id
            utbetFrekvens = "MND"
            oppdragGjelderId = new.personident.ident
            datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).toXMLDate()
            saksbehId = new.saksbehandlerId.ident
            avstemming115 = avstemming115(new.fagsystem.fagområde) 
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


private fun XMLGregorianCalendar.toLocalDate() = toGregorianCalendar().toZonedDateTime().toLocalDate()

private fun skalTilføreOpphørslinje(
    opphørsdato: LocalDate?,
    nyeLinjer: List<OppdragsLinje150>,
): Boolean {
    if (opphørsdato == null) return false
    return nyeLinjer.none { linje ->
        linje.datoVedtakFom.toLocalDate() <= opphørsdato
    }
}

private fun avstemming115(fagområde: String): Avstemming115 {
    val todayAtSeven = LocalDateTime.now().with(fixedTime)
    return objectFactory.createAvstemming115().apply {
        kodeKomponent = fagområde
        nokkelAvstemming = todayAtSeven.format("yyyy-MM-dd-HH.mm.ss.SSSSSS")
        tidspktMelding = todayAtSeven.format("yyyy-MM-dd-HH.mm.ss.SSSSSS")
    }
} 

fun opphørsdato(
    new: List<Utbetalingsperiode>,
    prev: List<Utbetalingsperiode>,
): LocalDate? {
    var førsteEndringIdx = prev.zip(new).indexOfFirst { it.first != it.second }

    when (førsteEndringIdx) {
        -1 if new.size > prev.size -> førsteEndringIdx = prev.size
        -1 if prev.size > new.size -> førsteEndringIdx = new.size
    }
    if (førsteEndringIdx >= prev.size) return null

    val opphørsdato = when {

        /** Siste periode er forkortet, og vi må opphøre ved slutten av den nye perioden
         * prev: ╭────────────────╮╭──────────────╮
         *       │                ││              │
         *       ╰────────────────╯╰──────────────╯
         * new:  ╭────────────────╮
         *       │                │
         *       ╰────────────────╯
         * res:                    ^
         *                         ╰ OPPHØR
         */
        førsteEndringIdx >= new.size -> {
            new.last().tom.plusDays(1)
        }

        /** En periode sin fom er forkortet, og vi må sette opphør fra og med forrige fom
         * prev: ╭────────────────────────────────╮
         *       │                                │
         *       ╰────────────────────────────────╯
         * new:                  ╭────────────────╮
         *                       │                │
         *                       ╰────────────────╯
         * res:  ^
         *       ╰ OPPHØR
         */
        prev[førsteEndringIdx].fom < new[førsteEndringIdx].fom -> {
            prev[førsteEndringIdx].fom
        }

        /** En periode sin tom er forkortet, og vi må sette opphør fra og med dagen etter den nye tom
         *  Dersom nyere perioder forekommer etterpå, kan "nyeLinjer" fjerne opphørsdatoen
         * prev: ╭────────────────╮╭──────────────╮
         *       │                ││              │
         *       ╰────────────────╯╰──────────────╯
         * new:  ╭────────╮        ╭──────────────╮
         *       │        │        │              │
         *       ╰────────╯        ╰──────────────╯
         * res:            ^
         *                 ╰ OPPHØR
         */
        new[førsteEndringIdx].tom < prev[førsteEndringIdx].tom -> {
            new[førsteEndringIdx].tom.plusDays(1)
        }

        else -> {
            null
        }
    }
    return opphørsdato
}

private fun nyeLinjer(
    new: Utbetaling,
    prev: Utbetaling,
): List<OppdragsLinje150> {
    var førsteEndringIdx = prev.perioder.zip(new.perioder).indexOfFirst { it.first != it.second }

    when {

        // De(n) nye endringen(e) kommer etter siste eksisterende periode.
        førsteEndringIdx == -1 && new.perioder.size > prev.perioder.size -> {
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
                && prev.perioder[førsteEndringIdx].fom.equals(new.perioder[førsteEndringIdx].fom)
            ) {
                førsteEndringIdx += 1
            }
        }
    }

    var sistePeriodeId = prev.lastPeriodeId
    return new.perioder
        .slice(førsteEndringIdx until new.perioder.size)
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
        datoKlassifikFom = periode.fom.toXMLDate()
        datoVedtakFom = periode.fom.toXMLDate()
        datoVedtakTom = periode.tom.toXMLDate()
        sats = BigDecimal.valueOf(periode.beløp.toLong())
        fradragTillegg = TfradragTillegg.T
        typeSats = utbetaling.periodetype.satstype
        brukKjoreplan = "N"
        saksbehId = utbetaling.saksbehandlerId.ident
        utbetalesTilId = utbetaling.personident.ident
        henvisning = utbetaling.behandlingId.id
        attestant180s.add(attestant)
        periode.vedtakssats?.let { sats ->
            vedtakssats157 = objectFactory.createVedtakssats157().apply {
                vedtakssats = BigDecimal.valueOf(sats.toLong())
            }
        }
    }
}

private fun avvent118(avvent: Avvent): Avvent118 {
    return objectFactory.createAvvent118().apply {
        datoAvventFom = avvent.fom.toXMLDate()
        datoAvventTom = avvent.tom.toXMLDate()
        datoOverfores = avvent.overføres?.toXMLDate()
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
