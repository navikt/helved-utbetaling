package simulering.models.soap

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import simulering.models.rest.*
import java.time.LocalDate

/**
 * Enhet kan være enten [tknr] eller [orgnr]+[avd]
 * 4 eller opptil 13 tegn
 */
typealias FnrOrgnr = String // 9-11 tegn
typealias Klasse = String // 0-20

object soap {
    data class SimuleringResponse(
        val simulerBeregningResponse: SimulerBeregningResponse,
    )

    data class SimulerBeregningResponse(
        val response: Response,
    )

    data class Response(
        val simulering: Beregning,
        val infomelding: Infomelding?,
    )

    // entiteten sin referanse-id 311
    data class Beregning(
        val gjelderId: FnrOrgnr,
        /** Ved simuleringsbereging gjelder dette datoen beregningen vil kjæres på. */
        val datoBeregnet: LocalDate,
        val belop: Double,
        val beregningsPeriode: List<Periode>,
    ) {
        fun intoDto(): rest.SimuleringResponse =
            rest.SimuleringResponse(
                gjelderId = gjelderId,
                datoBeregnet = datoBeregnet,
                totalBelop = belop.toInt(),
                perioder = beregningsPeriode.map(Periode::intoDto),
            )
    }

    // entiteten sin referanse-id 312
    data class Periode(
        val periodeFom: LocalDate,
        val periodeTom: LocalDate,
        val beregningStoppnivaa: List<Stoppnivå>,
    ) {
        fun intoDto(): rest.SimulertPeriode =
            rest.SimulertPeriode(
                fom = periodeFom,
                tom = periodeTom,
                utbetalinger = beregningStoppnivaa.map(Stoppnivå::intoDto),
            )
    }

    // entiteten sin referanse-id 313
    data class Stoppnivå(
        val kodeFagomraade: String,
        val fagsystemId: String,
        val utbetalesTilId: FnrOrgnr,
        val forfall: LocalDate,
        val feilkonto: Boolean,
        val beregningStoppnivaaDetaljer: List<Detalj>,
    ) {
        fun intoDto(): rest.Utbetaling =
            rest.Utbetaling(
                fagområde = kodeFagomraade.trimEnd(),
                fagSystemId = fagsystemId.trimEnd(),
                utbetalesTilId = utbetalesTilId.removePrefix("00"),
                forfall = forfall,
                feilkonto = feilkonto,
                detaljer = beregningStoppnivaaDetaljer.map(Detalj::intoDto),
            )
    }

    // entiteten sin referanse-id 314
    data class Detalj(
        val faktiskFom: LocalDate,
        val faktiskTom: LocalDate,
        val belop: Double,
        val trekkVedtakId: Long,
        val sats: Double,
        val typeSats: SatsType?,
        val klassekode: Klasse,
        val typeKlasse: Klasse,
        val refunderesOrgNr: FnrOrgnr,
    ) {
        fun intoDto(): rest.Postering =
            rest.Postering(
                type = typeKlasse,
                faktiskFom = faktiskFom,
                faktiskTom = faktiskTom,
                belop = belop.toInt(),
                sats = sats,
                satstype = typeSats?.name,
                klassekode = klassekode.trimEnd(),
                trekkVedtakId = if (trekkVedtakId == 0L) null else trekkVedtakId,
                refunderesOrgNr = refunderesOrgNr.removePrefix("00").trimEnd().takeIf { it.isNotBlank() },
            )
    }

    /**
     * Satstyper for trekk:
     * LOPD/LOPM/LOPP: Løpende trekk hhv. dagsats, månedssats, prosentsats
     * SALD/SALM/SALP: Saldotrekk hhv. dagsats, månedssats, prosentsats
     */
    enum class SatsType {
        DAG,
        DAG7,
        UKE,
        `14DB`,
        MND,
        AAR,
        ENG,
        AKTO,
        LOPD,
        LOPM,
        LOPP,
        SALD,
        SALM,
        SALP,
    }

    data class Infomelding(
        val beskrMelding: String,
    )

    data class Fault(
        val faultcode: String,
        val faultstring: String,
    )

    @JacksonXmlRootElement(localName = "ns3:simulerBeregningRequest")
    data class SimulerBeregningRequest(
        @JacksonXmlProperty(isAttribute = true, localName = "ns2")
        val ns2: String = "http://nav.no/system/os/entiteter/oppdragSkjema",
        @JacksonXmlProperty(isAttribute = true, localName = "ns3")
        val ns3: String = "http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt",
        @JacksonXmlProperty(isAttribute = true)
        val request: SimulerRequest,
    ) {
        companion object {
            fun from(dto: rest.SimuleringRequest): SimulerBeregningRequest =
                SimulerBeregningRequest(
                    request =
                        SimulerRequest(
                            oppdrag =
                                Oppdrag(
                                    kodeFagomraade = dto.fagområde,
                                    kodeEndring = if (dto.erFørsteUtbetalingPåSak) "NY" else "ENDR",
                                    utbetFrekvens = "MND",
                                    fagsystemId = dto.sakId,
                                    oppdragGjelderId = dto.personident.verdi,
                                    saksbehId = dto.saksbehandler,
                                    datoOppdragGjelderFom = LocalDate.EPOCH,
                                    enhet = listOf(Enhet(typeEnhet = "BOS", enhet = "8020", LocalDate.EPOCH)),
                                    oppdragslinje = dto.utbetalingsperioder.map { Oppdragslinje.from(it, dto) },
                                ),
                            simuleringsPeriode =
                                SimuleringsPeriode(
                                    datoSimulerFom = dto.finnSimuleringFom(),
                                    datoSimulerTom = dto.utbetalingsperioder.maxBy { it.tom }.tom,
                                ),
                        ),
                )
        }
    }

    private fun rest.SimuleringRequest.finnSimuleringFom(): LocalDate {
        val tidligstePeriode = this.utbetalingsperioder.minBy { it.fom }
        val opphør = this.utbetalingsperioder.find { it.opphør != null }?.opphør
        return if (opphør != null) {
            minOf(tidligstePeriode.fom, opphør.fom)
        } else {
            tidligstePeriode.fom
        }
    }

    data class SimulerRequest(
        val oppdrag: Oppdrag,
        val simuleringsPeriode: SimuleringsPeriode,
    )

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
        val oppdragslinje: List<Oppdragslinje>,
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

    data class SimuleringsPeriode(
        val datoSimulerFom: LocalDate,
        val datoSimulerTom: LocalDate,
    )

    data class RefusjonsInfo(
        val refunderesId: String,
        val datoFom: LocalDate,
        val maksDato: LocalDate?,
    )

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
        @JsonProperty("ns2:attestant")
        val attestant: List<Attestant>,
    ) {
        companion object {
            fun from(
                utbetalingsperiode: rest.Utbetalingsperiode,
                dto: rest.SimuleringRequest,
            ): Oppdragslinje =
                Oppdragslinje(
                    delytelseId = "${dto.sakId}#${utbetalingsperiode.periodeId}",
                    refDelytelseId =
                        if (utbetalingsperiode.erEndringPåEksisterendePeriode) {
                            null
                        } else {
                            utbetalingsperiode.forrigePeriodeId
                                ?.let {
                                    "${dto.sakId}#$it"
                                }
                        },
                    refFagsystemId =
                        if (utbetalingsperiode.erEndringPåEksisterendePeriode) {
                            null
                        } else {
                            utbetalingsperiode.forrigePeriodeId
                                ?.let { dto.sakId }
                        },
                    kodeEndringLinje = if (utbetalingsperiode.erEndringPåEksisterendePeriode) "ENDR" else "NY",
                    kodeKlassifik = utbetalingsperiode.klassekode,
                    kodeStatusLinje = utbetalingsperiode.opphør?.let { KodeStatusLinje.OPPH },
                    datoStatusFom = utbetalingsperiode.opphør?.fom,
                    datoVedtakFom = utbetalingsperiode.fom,
                    datoVedtakTom = utbetalingsperiode.tom,
                    sats = utbetalingsperiode.sats,
                    fradragTillegg = FradragTillegg.T,
                    typeSats = utbetalingsperiode.satstype.verdi,
                    saksbehId = dto.saksbehandler,
                    brukKjoreplan = "N",
                    attestant = listOf(Attestant(dto.saksbehandler)),
                    utbetalesTilId = utbetalingsperiode.utbetalesTil,
                )
        }

        var refusjonsInfo: RefusjonsInfo? = null
    }

    enum class FradragTillegg {
        F,
        T,
    }

    enum class KodeStatusLinje {
        OPPH,
        HVIL,
        SPER,
        REAK,
    }

    data class Attestant(
        val attestantId: String,
    )
}
