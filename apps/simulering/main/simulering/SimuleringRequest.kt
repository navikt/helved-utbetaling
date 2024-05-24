package simulering

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
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
                        enhet = listOf(Enhet(typeEnhet = "BOS", enhet = "8020", LocalDate.EPOCH)),
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

@JsonPropertyOrder(
    "kodeEndring",
    "kodeFagomraade",
    "fagsystemId",
    "utbetFrekvens",
    "oppdragGjelderId",
    "datoOppdragGjelderFom",
    "saksbehId",
    "ns2:enhet",
    "oppdragslinje",
)
data class Oppdrag(
    val kodeEndring: String,
    val kodeFagomraade: String,
    val fagsystemId: String,
    val utbetFrekvens: String,
    val oppdragGjelderId: String,
    val datoOppdragGjelderFom: LocalDate?,
    val saksbehId: String,
    @JsonProperty("ns2:enhet")
    val enhet: List<Enhet>,
    val oppdragslinje: List<Oppdragslinje>
)

@JsonPropertyOrder(
    "typeEnhet",
    "enhet",
    "datoEnhetFom",
)
data class Enhet(
    val typeEnhet: String,
    val enhet: String,
    val datoEnhetFom: LocalDate?,
)

data class SimuleringsPeriode(val datoSimulerFom: LocalDate, val datoSimulerTom: LocalDate)
data class RefusjonsInfo(val refunderesId: String, val datoFom: LocalDate, val maksDato: LocalDate?)

@JsonPropertyOrder(
    "kodeEndringLinje",
    "kodeStatusLinje",
    "datoStatusFom",
    "delytelseId",
    "kodeKlassifik",
    "datoVedtakFom",
    "datoVedtakTom",
    "sats",
    "fradragTillegg",
    "typeSats",
    "brukKjoreplan",
    "saksbehId",
    "utbetalesTilId",
    "refFagsystemId",
    "refDelytelseId",
    "ns2:grad",
    "ns2:attestant",
)
data class Oppdragslinje(
    val kodeEndringLinje: String,
    val kodeStatusLinje: KodeStatusLinje?,
    val datoStatusFom: LocalDate?,
    val delytelseId: String,
    val kodeKlassifik: String,
    val datoVedtakFom: LocalDate,
    val datoVedtakTom: LocalDate,
    val sats: Int,
    val fradragTillegg: FradragTillegg,
    val typeSats: String,
    val brukKjoreplan: String,
    val saksbehId: String,
    val utbetalesTilId: String?,
    val refFagsystemId: String?,
    val refDelytelseId: String?,
    @JsonProperty("ns2:grad")
    val grad: List<Grad>,
    @JsonProperty("ns2:attestant")
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
                grad = listOf(Grad(typeGrad = request.grad.type.name, grad = request.grad.prosent)),
                attestant = listOf(Attestant(saksbehandler)),
                utbetalesTilId = request.utbetalesTil
            )
        }
    }

    var refusjonsInfo: RefusjonsInfo? = null
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