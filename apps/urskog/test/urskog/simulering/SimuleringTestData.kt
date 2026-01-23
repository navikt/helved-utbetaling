package urskog.simulering

import no.nav.system.os.entiteter.oppdragskjema.Enhet
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg
import no.nav.system.os.entiteter.typer.simpletypes.KodeStatusLinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.ObjectFactory
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import no.nav.system.os.entiteter.oppdragskjema.ObjectFactory as OppdragFactory
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.ObjectFactory as RootFactory

private val rootFactory = RootFactory()
private val objectFactory = ObjectFactory()
private val oppdragFactory = OppdragFactory()

private fun LocalDate.format() = format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))

fun simulering(
    oppdragslinjer: List<Oppdragslinje> = listOf(oppdragslinje()),
    kodeEndring: String = "NY",               // NY/ENDR
    fagområde: String = "AAP",
    fagsystemId: String = "1",                // sakid
    oppdragGjelderId: String = "12345678910", // personident 
    saksbehId: String = "Z999999",
    enhet: String? = null,                    // betalende enhet (lokalkontor)
): SimulerBeregningRequest {
    val oppdrag = objectFactory.createOppdrag().apply {
        this.kodeEndring = kodeEndring
        this.kodeFagomraade = fagområde
        this.fagsystemId = fagsystemId
        this.utbetFrekvens = "MND"
        this.oppdragGjelderId = oppdragGjelderId
        this.datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).format()
        this.saksbehId = saksbehId
        this.enhets.addAll(enheter(enhet))
        oppdragslinjes.addAll(oppdragslinjer)
    }
    return rootFactory.createSimulerBeregningRequest().apply {
        request = objectFactory.createSimulerBeregningRequest().apply {
            this.oppdrag = oppdrag
        }
    }
}

fun oppdragslinje(
    delytelsesId: String = "DEL 1",
    sats: Long = 700,
    datoVedtakFom: LocalDate = LocalDate.now(),
    datoVedtakTom: LocalDate = LocalDate.now(),
    typeSats: String = "DAG", // DAG/DAG7/MND/ENG
    refDelytelsesId: String? = null,
    kodeEndring: String = "NY", // NY/ENDR
    opphør: LocalDate? = null,
    fagsystemId: String = "1", // sakid
    klassekode: String = "AAPOR",
    saksbehId: String = "Z999999", // saksbehandler
    beslutterId: String = "Z999999", // beslutter
    utbetalesTilId: String = "12345678910", // personident
): Oppdragslinje {
    val attestant = oppdragFactory.createAttestant().apply {
        this.attestantId = beslutterId
    }
    return objectFactory.createOppdragslinje().apply {
        this.kodeEndringLinje = kodeEndring
        opphør?.let {
            this.kodeStatusLinje = KodeStatusLinje.OPPH
            this.datoStatusFom = opphør.format()
        }
        refDelytelsesId?.let {
            this.refDelytelseId = refDelytelsesId
            this.refFagsystemId = fagsystemId
        }
        this.delytelseId = delytelsesId
        this.kodeKlassifik = klassekode
        this.datoVedtakFom = datoVedtakFom.format()
        this.datoVedtakTom = datoVedtakTom.format()
        this.sats = BigDecimal.valueOf(sats)
        this.fradragTillegg = FradragTillegg.T
        this.typeSats = typeSats
        this.brukKjoreplan = "N"
        this.saksbehId = saksbehId
        this.utbetalesTilId = utbetalesTilId
        this.attestants.add(attestant)
    }
}

fun enheter(enhet: String? = null): List<Enhet> {
    val bos = oppdragFactory.createEnhet().apply {
        this.enhet = enhet ?: "8020"
        this.typeEnhet = "BOS"
        this.datoEnhetFom = LocalDate.of(1970, 1, 1).format()
    }
    val beh = oppdragFactory.createEnhet().apply {
        this.enhet = "8020"
        this.typeEnhet = "BEH"
        this.datoEnhetFom = LocalDate.of(1970, 1, 1).format()
    }
    return when (enhet) {
        null -> listOf(bos)
        else -> listOf(bos, beh)
    }
}

