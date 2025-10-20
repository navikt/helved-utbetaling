package abetal

import models.*
import no.nav.system.os.entiteter.oppdragskjema.Enhet
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg
import no.nav.system.os.entiteter.typer.simpletypes.KodeStatusLinje
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import no.nav.system.os.entiteter.oppdragskjema.ObjectFactory as OppdragFactory
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.ObjectFactory
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje

private val rootFactory = ObjectFactory()
private val objectFactory = no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.ObjectFactory()
private val oppdragFactory = OppdragFactory()

private fun LocalDate.format() = format(DateTimeFormatter.ofPattern("YYYY-MM-dd"))

object SimuleringService {

    fun opprett(new: Utbetaling): SimulerBeregningRequest {
        var forrigeId: PeriodeId? = null
        val oppdrag = objectFactory.createOppdrag().apply {
            kodeEndring = if (new.førsteUtbetalingPåSak) "NY" else "ENDR"
            kodeFagomraade = new.fagsystem.fagområde
            fagsystemId = new.sakId.id
            utbetFrekvens = "MND"
            oppdragGjelderId = new.personident.ident
            datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).format()
            saksbehId = new.saksbehandlerId.ident
            enhets.addAll(enheter(new))
            new.perioder.mapIndexed { i, periode ->
                val periodeId = if (i == new.perioder.size - 1) new.lastPeriodeId else PeriodeId()
                val oppdragslinje = oppdragslinje(new, false, periode, periodeId, forrigeId.also { forrigeId = periodeId }, null )
                oppdragslinjes.add(oppdragslinje)
            }
        }

        return rootFactory.createSimulerBeregningRequest().apply {
            request = objectFactory.createSimulerBeregningRequest().apply {
                this.oppdrag = oppdrag
            }
        }
    }

    fun update(new: Utbetaling, prev: Utbetaling): SimulerBeregningRequest {
        prev.validateLockedFields(new)
        prev.validateMinimumChanges(new)

        val oppdrag = objectFactory.createOppdrag().apply {
            kodeEndring = "ENDR"
            kodeFagomraade = new.fagsystem.fagområde
            fagsystemId = new.sakId.id
            utbetFrekvens = "MND"
            oppdragGjelderId = new.personident.ident
            datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).format()
            saksbehId = new.saksbehandlerId.ident
            enhets.addAll(enheter(new))
            val prev = prev.copy(perioder = prev.perioder.sortedBy { it.fom }) // assure its sorted
            val new = new.copy(perioder = new.perioder.sortedBy { it.fom }) // assure its sorted
            val opphørsdato = opphørsdato(new.perioder, prev.perioder, new.periodetype)
            val opphørslinje = oppdragslinje(new, true, prev.perioder.last(), prev.lastPeriodeId, null, opphørsdato )
            if (opphørsdato != null) oppdragslinjes.add(opphørslinje)
            oppdragslinjes.addAll(nyeLinjer(new, prev, opphørsdato))
        }
        return rootFactory.createSimulerBeregningRequest().apply {
            request = objectFactory.createSimulerBeregningRequest().apply {
                this.oppdrag = oppdrag
            }
        }
    }

    fun delete(new: Utbetaling, prev: Utbetaling): SimulerBeregningRequest {
        prev.validateLockedFields(new)

        val oppdrag = objectFactory.createOppdrag().apply {
            kodeEndring = "ENDR"
            kodeFagomraade = new.fagsystem.fagområde
            fagsystemId = new.sakId.id
            utbetFrekvens = "MND"
            oppdragGjelderId = new.personident.ident
            datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).format()
            saksbehId = new.saksbehandlerId.ident
            enhets.addAll(enheter(new))
            val lastPeriode = new.perioder.maxBy { it.fom }
            val opphør = new.perioder.minBy { it.fom }.fom
            val oppdragslinje = oppdragslinje(new, false, lastPeriode, prev.lastPeriodeId, null, opphør)
            oppdragslinjes.add(oppdragslinje)
        }
        return rootFactory.createSimulerBeregningRequest().apply {
            request = objectFactory.createSimulerBeregningRequest().apply {
                this.oppdrag = oppdrag
            }
        }
    }
}

private fun enheter(new: Utbetaling): List<Enhet> {
    val bos = oppdragFactory.createEnhet().apply {
        enhet = new.perioder.betalendeEnhet()?.enhet ?: "8020"
        typeEnhet = "BOS"
        datoEnhetFom = LocalDate.of(1970, 1, 1).format()
    }
    val beh = oppdragFactory.createEnhet().apply {
        enhet = "8020"
        typeEnhet = "BEH"
        datoEnhetFom = LocalDate.of(1970, 1, 1).format()
    }
    return when (new.perioder.betalendeEnhet()) {
        null -> listOf(bos)
        else -> listOf(bos, beh)
    }
}

private fun oppdragslinje(
    utbetaling: Utbetaling,
    erEndringPåEksisterendePeriode: Boolean,
    periode: Utbetalingsperiode,
    periodeId: PeriodeId,
    forrigePeriodeId: PeriodeId?,
    opphør: LocalDate?,
): Oppdragslinje {
    val attestant = oppdragFactory.createAttestant().apply {
        attestantId = utbetaling.beslutterId.ident
    }
    return objectFactory.createOppdragslinje().apply {
        kodeEndringLinje = if (erEndringPåEksisterendePeriode) "ENDR" else "NY"
        opphør?.let {
            kodeStatusLinje = KodeStatusLinje.OPPH
            datoStatusFom = opphør.format()
        }
        forrigePeriodeId?.let {
            refDelytelseId = forrigePeriodeId.toString()
            refFagsystemId = utbetaling.sakId.id
        }
        delytelseId = periodeId.toString()
        kodeKlassifik = utbetaling.stønad.klassekode
        datoKlassifikFom = periode.fom.format()
        datoVedtakFom = periode.fom.format()
        datoVedtakTom = periode.tom.format()
        sats = BigDecimal.valueOf(periode.beløp.toLong())
        fradragTillegg = FradragTillegg.T
        typeSats = utbetaling.periodetype.satstype
        brukKjoreplan = "N"
        saksbehId = utbetaling.saksbehandlerId.ident
        utbetalesTilId = utbetaling.personident.ident
        attestants.add(attestant)
    }
}

private fun nyeLinjer(
    new: Utbetaling,
    prev: Utbetaling,
    opphørsdato: LocalDate?,
): List<Oppdragslinje> {
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
                && prev.perioder[førsteEndringIdx].fom.equals(new.perioder[førsteEndringIdx].fom)
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
            oppdragslinje(new, false, p, pid, sistePeriodeId, null).also {
                sistePeriodeId = pid
            }
        }
}
