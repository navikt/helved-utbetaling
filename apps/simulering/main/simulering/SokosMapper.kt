package simulering

import models.Fagsystem
import models.Info
import models.Simulering
import models.v2
import no.nav.system.os.entiteter.beregningskjema.Beregning
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaa
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaaDetaljer
import no.nav.system.os.entiteter.beregningskjema.BeregningsPeriode
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse
import no.nav.system.os.entiteter.oppdragskjema.Enhet
import no.nav.system.os.entiteter.oppdragskjema.Tekst as SoapTekst
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje as SoapLinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningResponse as SoapResponse

fun SimulerBeregningRequest.toRestRequest(): SimuleringRequest {
    val source = request.oppdrag
    require(source.belopsgrenses.isEmpty()) { "REST contract does not support belopsgrense" }
    val input = RequestSimulering(
        oppdrag = Oppdrag(
            source.kodeEndring, source.kodeStatus?.value(), source.datoStatusFom,
            source.kodeFagomraade, source.fagsystemId, source.oppdragsId,
            source.utbetFrekvens, source.datoForfall, source.stonadId,
            source.oppdragGjelderId, source.datoOppdragGjelderFom, source.saksbehId
        ),
        simuleringsPeriode = request.simuleringsPeriode?.let { Simuleringsperiode(it.datoSimulerFom, it.datoSimulerTom) },
        bilagstype = source.bilagstypes.map { Bilagstype(it.typeBilag) },
        avstemmingsnokkel = source.avstemmingsnokkels.map { Avstemmingsnokkel(it.kodeKomponent, it.avstemmingsNokkel, it.tidspktReg) },
        omposter = listOfNotNull(source.ompostering?.let { Omposter(it.omPostering, it.datoOmposterFom, it.feilreg.toRestBoolean()) }),
        enhetOppdrag = source.enhets.map(Enhet::toRest),
        tekstOppdrag = source.teksts.map(SoapTekst::toRest),
        oppdragslinje = source.oppdragslinjes.map {
            require(it.fradragTillegg?.value() != "F") { "REST contract does not support fradragTillegg=F" }
            it.toRest()
        }
    )
    return SimuleringRequest(SimuleringOperation(SimuleringInput(input)))
}

private fun SoapLinje.toRest() = Oppdragslinje(
    kodeEndringLinje = kodeEndringLinje,
    kodeStatusLinje = kodeStatusLinje?.value(),
    datoStatusFom = datoStatusFom,
    vedtakId = vedtakId,
    delytelseId = delytelseId,
    linjeId = linjeId,
    kodeKlassifik = kodeKlassifik,
    datoKlassifikFom = datoKlassifikFom,
    datoVedtakFom = datoVedtakFom,
    datoVedtakTom = datoVedtakTom,
    sats = sats,
    typeSats = typeSats,
    skyldnerId = skyldnerId,
    datoSkyldnerFom = datoSkyldnerFom,
    kravhaverId = kravhaverId,
    datoKravhaverFom = datoKravhaverFom,
    kid = kid,
    datoKidFom = datoKidFom,
    brukKjoreplan = brukKjoreplan.toRestBoolean(),
    saksbehId = saksbehId,
    utbetalesTilId = utbetalesTilId,
    datoUtbetalesTilIdFom = datoUtbetalesTilIdFom,
    kodeArbeidsgiver = kodeArbeidsgiver?.value(),
    henvisning = henvisning,
    typeSoknad = typeSoknad,
    refFagsystemId = refFagsystemId,
    refOppdragsId = refOppdragsId,
    refDelytelseId = refDelytelseId,
    refLinjeId = refLinjeId,
    refusjonsInfo = listOfNotNull(refusjonsInfo?.let { RefusjonsInfo(it.refunderesId, it.maksDato, it.datoFom) }),
    tekstOppdragslinje = teksts.map(SoapTekst::toRest),
    enhetOppdragslinje = enhets.map(Enhet::toRest),
    gradOppdragslinje = grads.map { Grad(it.typeGrad, it.grad) },
    attestant = attestants.map { Attestant(it.attestantId, it.datoUgyldigFom) },
    valutaOppdragslinje = valutas.map { Valuta(it.typeValuta, it.valuta, it.datoValutaFom, it.feilreg.toRestBoolean()) }
)

private fun Enhet.toRest() = Oppdragsenhet(typeEnhet, enhet, datoEnhetFom)
private fun SoapTekst.toRest() = Tekst(tekstLnr, tekstKode, tekst, datoTekstFom, datoTekstTom, feilreg.toRestBoolean())

private fun String?.toRestBoolean(): Boolean? = when (this?.trim()?.uppercase()) {
    null, "" -> null
    "J", "TRUE", "1" -> true
    "N", "FALSE", "0" -> false
    else -> error("Unknown boolean flag in SOAP request")
}

// Reuse existing domain mappings so comparison does not introduce different arithmetic.
fun ResponsSimulering.toSimulering(fagsystem: Fagsystem): Simulering {
    val message = toBusinessErrorMessage()
    if (message != null) return Info.UgyldigRequest(fagsystem, message)
    if (statusKode !in listOf(0, 4)) {
        return Info.Feilet(fagsystem, "Sokos status $statusKode: ${statusMelding.orEmpty()}")
    }
    return beregning.toSoapResponse().toSimulering(fagsystem)
}

// Temporary normalization of observed REST/SOAP differences while SOAP remains authoritative.
private fun ResponsSimulering.toBusinessErrorMessage(): String? = when {
    statusKode == 8 && statusMelding == "B110008F - Oppdraget finnes fra før" ->
        "Utbetaling med SakId/BehandlingId finnes fra før"
    statusKode == 4 && statusMelding == "Navn er ikke funnet ved oppslag" && beregning.isEmpty() ->
        "Personen finnes ikke i PDL"
    else -> null
}

private fun RestBeregning?.isEmpty(): Boolean = this == null || (
    gjelderId.isBlank() && datoBeregnet.isBlank()
        && belopTotalt.signum() == 0 && periodeStoppnivaa.isEmpty()
    )

fun SimulerBeregningResponse.toSimulering(fagsystem: Fagsystem): Simulering = when {
    fagsystem.isTilleggsstønader() || fagsystem == Fagsystem.TILTAKSPENGER ->
        simulering.v1.from(this) ?: Info.OkUtenEndring(fagsystem)
    else -> v2.Simulering.from(this)
}

private fun RestBeregning?.toSoapResponse() = SimulerBeregningResponse().also { envelope ->
    if (this != null) envelope.response = SoapResponse().also { response ->
        response.simulering = Beregning().also { result ->
            result.gjelderId = gjelderId
            result.datoBeregnet = datoBeregnet.trimEnd()
            result.belop = belopTotalt
            periodeStoppnivaa.groupBy { it.beregningsPeriode }.forEach { (dates, entries) ->
                result.beregningsPeriodes.add(BeregningsPeriode().also { period ->
                    period.periodeFom = dates.periodeFom.trimEnd()
                    period.periodeTom = dates.periodeTom.trimEnd()
                    period.beregningStoppnivaas.addAll(entries.map { it.beregningStoppnivaa.toSoap() })
                })
            }
        }
    }
}

private fun RestStoppnivaa.toSoap() = BeregningStoppnivaa().also { stop ->
    stop.kodeFagomraade = kodeFagomraade
    stop.fagsystemId = fagsystemId
    stop.utbetalesTilId = utbetalesTilId
    stop.beregningStoppnivaaDetaljers.addAll(stoppnivaaDetaljer.map { detail ->
        BeregningStoppnivaaDetaljer().also {
            it.faktiskFom = detail.faktiskFom.trimEnd()
            it.faktiskTom = detail.faktiskTom.trimEnd()
            it.belop = detail.belop
            it.klassekode = detail.klassekode
            it.typeKlasse = detail.typeKlasse
        }
    })
}
