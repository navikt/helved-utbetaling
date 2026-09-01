package simulering

import no.nav.system.os.entiteter.oppdragskjema.Enhet
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg
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
    fagområde: String = "TILLST",
    fagsystemId: String = "1",
    oppdragGjelderId: String = "12345678910",
): SimulerBeregningRequest {
    val oppdragslinje = objectFactory.createOppdragslinje().apply {
        this.kodeEndringLinje = "NY"
        this.delytelseId = "DEL 1"
        this.kodeKlassifik = "AAPOR"
        this.datoVedtakFom = LocalDate.now().format()
        this.datoVedtakTom = LocalDate.now().format()
        this.sats = BigDecimal.valueOf(700)
        this.fradragTillegg = FradragTillegg.T
        this.typeSats = "DAG"
        this.brukKjoreplan = "N"
        this.saksbehId = "Z999999"
        this.utbetalesTilId = oppdragGjelderId
        this.attestants.add(oppdragFactory.createAttestant().apply { attestantId = "Z999999" })
    }
    val oppdrag = objectFactory.createOppdrag().apply {
        this.kodeEndring = "NY"
        this.kodeFagomraade = fagområde
        this.fagsystemId = fagsystemId
        this.utbetFrekvens = "MND"
        this.oppdragGjelderId = oppdragGjelderId
        this.datoOppdragGjelderFom = LocalDate.of(2000, 1, 1).format()
        this.saksbehId = "Z999999"
        this.enhets.add(oppdragFactory.createEnhet().apply {
            this.enhet = "8020"
            this.typeEnhet = "BOS"
            this.datoEnhetFom = LocalDate.of(1970, 1, 1).format()
        })
        this.oppdragslinjes.add(oppdragslinje)
    }
    return rootFactory.createSimulerBeregningRequest().apply {
        request = objectFactory.createSimulerBeregningRequest().apply {
            this.oppdrag = oppdrag
        }
    }
}
