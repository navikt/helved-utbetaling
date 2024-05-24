package simulering

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import simulering.dto.SimuleringApiDto
import simulering.dto.Utbetalingslinje
import java.time.LocalDate

@JacksonXmlRootElement(localName = "ns3:simulerBeregningRequest")
data class SimulerBeregning(
    @JacksonXmlProperty(isAttribute = true, localName = "ns2")
    val ns2: String = "http://nav.no/system/os/entiteter/oppdragSkjema",
    @JacksonXmlProperty(isAttribute = true, localName = "ns3")
    val ns3: String = "http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt",
    @JacksonXmlProperty(isAttribute = true)
    val request: SimulerRequest
) {
    companion object {
        fun from(dto: SimuleringApiDto): SimulerBeregning {
            return SimulerBeregning(
                request = SimulerRequest(
                    oppdrag = Oppdrag(
                        kodeFagomraade = dto.fagområde,
                        kodeEndring = dto.endringskode.verdi,
                        utbetFrekvens = dto.utbetalingsfrekvens.verdi,
                        fagsystemId = dto.fagsystemId,
                        oppdragGjelderId = dto.personident.verdi,
                        saksbehId = dto.saksbehandler,
                        datoOppdragGjelderFom = LocalDate.EPOCH,
                        enhet = listOf(Enhet("8020", "BOS", LocalDate.EPOCH)),
                        oppdragslinje = dto.utbetalingslinjer.map { Oppdragslinje.from(it, dto.saksbehandler) }
                    ),
                    simuleringsPeriode = SimuleringsPeriode(
                        datoSimulerFom = dto.utbetalingslinjer.minBy { it.fom }.fom,
                        datoSimulerTom = dto.utbetalingslinjer.maxBy { it.tom }.tom,
                    )
                )
            )
        }
    }
}

data class SimulerRequest(val oppdrag: Oppdrag, val simuleringsPeriode: SimuleringsPeriode)
data class Oppdrag(
    val kodeFagomraade: String,
    val kodeEndring: String,
    val utbetFrekvens: String,
    val fagsystemId: String,
    val oppdragGjelderId: String,
    val saksbehId: String,
    val datoOppdragGjelderFom: LocalDate?,
    @JacksonXmlProperty(isAttribute = true, localName = "ns2:enhet")
    val enhet: List<Enhet>,
    val oppdragslinje: List<Oppdragslinje>
)

data class Enhet(val enhet: String, val typeEnhet: String, val datoEnhetFom: LocalDate?)
data class SimuleringsPeriode(val datoSimulerFom: LocalDate, val datoSimulerTom: LocalDate)
data class RefusjonsInfo(val refunderesId: String, val datoFom: LocalDate, val maksDato: LocalDate?)

data class Oppdragslinje(
    val delytelseId: String,
    val refDelytelseId: String?,
    val refFagsystemId: String?,
    val kodeEndringLinje: String,
    val kodeKlassifik: String,
    val kodeStatusLinje: KodeStatusLinje?,
    val datoStatusFom: LocalDate?,
    val datoVedtakFom: LocalDate,
    val datoVedtakTom: LocalDate,
    val sats: Int,
    val fradragTillegg: FradragTillegg,
    val typeSats: String,
    val saksbehId: String,
    val brukKjoreplan: String,
    @JacksonXmlProperty(isAttribute = true, localName = "ns2:grad")
    val grad: List<Grad>,
    @JacksonXmlProperty(isAttribute = true, localName = "ns2:attestant")
    val attestant: List<Attestant>
) {

    companion object {
        fun from(request: Utbetalingslinje, saksbehandler: String): Oppdragslinje {
            return Oppdragslinje(
                delytelseId = request.delytelseId,
                refDelytelseId = request.refDelytelseId,
                refFagsystemId = request.refFagsystemId,
                kodeEndringLinje = request.endringskode.verdi,
                kodeKlassifik = request.klassekode,
                kodeStatusLinje = request.statuskode?.let { KodeStatusLinje.valueOf(it) },
                datoStatusFom = request.datoStatusFom,
                datoVedtakFom = request.fom,
                datoVedtakTom = request.tom,
                sats = request.sats,
                fradragTillegg = FradragTillegg.T,
                typeSats = request.satstype.verdi,
                saksbehId = saksbehandler,
                brukKjoreplan = "N",
                grad = listOf(Grad(typeGrad = "UFOR", grad = request.grad)),
                attestant = listOf(Attestant(saksbehandler)),
            )
        }
    }

    var refusjonsInfo: RefusjonsInfo? = null
    var utbetalesTilId: String? = null
}

enum class FradragTillegg {
    F, T
}

enum class KodeStatusLinje {
    OPPH,
    HVIL,
    SPER,
    REAK;
}

data class Grad(val typeGrad: String, val grad: Int?)
data class Attestant(val attestantId: String)