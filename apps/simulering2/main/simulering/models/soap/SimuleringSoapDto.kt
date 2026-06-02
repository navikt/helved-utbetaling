package simulering.models.soap

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlElement
import simulering.models.rest.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Enhet kan være enten [tknr] eller [orgnr]+[avd]
 * 4 eller opptil 13 tegn
 */
typealias FnrOrgnr = String // 9-11 tegn
typealias Klasse = String // 0-20

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object NullableSatsTypeSerializer : KSerializer<soap.SatsType?> {
    override val descriptor = PrimitiveSerialDescriptor("SatsType", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: soap.SatsType?) {
        if (value == null) encoder.encodeString("") else encoder.encodeString(value.name)
    }
    override fun deserialize(decoder: Decoder): soap.SatsType? {
        val str = decoder.decodeString()
        return if (str.isBlank()) null else soap.SatsType.valueOf(str)
    }
}

object soap {
    @Serializable
    data class SimuleringResponse(
        val simulerBeregningResponse: SimulerBeregningResponse,
    )

    @Serializable
    data class SimulerBeregningResponse(
        val response: Response?,
    )

    @Serializable
    data class Response(
        val simulering: Beregning,
        val infomelding: Infomelding?,
    )

    @Serializable
    data class Beregning(
        val gjelderId: FnrOrgnr,
        @Serializable(with = LocalDateSerializer::class)
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

        companion object {
            fun empty(request: SimulerBeregningRequest) = Beregning(
                gjelderId = request.request.oppdrag.oppdragGjelderId,
                datoBeregnet = LocalDate.now(),
                belop = 0.0,
                beregningsPeriode = emptyList()
            )
        }
    }

    @Serializable
    @XmlSerialName("beregningsPeriode", "", "")
    data class Periode(
        @Serializable(with = LocalDateSerializer::class)
        val periodeFom: LocalDate,
        @Serializable(with = LocalDateSerializer::class)
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

    @Serializable
    @XmlSerialName("beregningStoppnivaa", "", "")
    data class Stoppnivå(
        val kodeFagomraade: String,
        val fagsystemId: String,
        val utbetalesTilId: FnrOrgnr,
        @Serializable(with = LocalDateSerializer::class)
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

    @Serializable
    @XmlSerialName("beregningStoppnivaaDetaljer", "", "")
    data class Detalj(
        @Serializable(with = LocalDateSerializer::class)
        val faktiskFom: LocalDate,
        @Serializable(with = LocalDateSerializer::class)
        val faktiskTom: LocalDate,
        val belop: Double,
        val trekkVedtakId: Long,
        val sats: Double,
        @XmlElement(true)
        @XmlSerialName("typeSats", "", "")
        @Serializable(with = NullableSatsTypeSerializer::class)
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

    @Serializable
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

    @Serializable
    data class Infomelding(
        val beskrMelding: String,
    )

    @Serializable
    data class Fault(
        val faultcode: String,
        val faultstring: String,
    )

    @Serializable
    @XmlSerialName("simulerBeregningRequest", "http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt", "ns3")
    data class SimulerBeregningRequest(
        val request: SimulerRequest,
    ) {
        companion object {
            fun from(dto: UtbetalingsoppdragDto) = SimulerBeregningRequest(
                request = SimulerRequest(
                    oppdrag = Oppdrag(
                        kodeFagomraade = dto.fagsystem.kode,
                        kodeEndring = if (dto.erFørsteUtbetalingPåSak) "NY" else "ENDR",
                        utbetFrekvens = dto.fagsystem.utbetalingFrekvens(),
                        fagsystemId = dto.saksnummer,
                        oppdragGjelderId = dto.aktør,
                        saksbehId = dto.saksbehandlerId,
                        datoOppdragGjelderFom = LocalDate.EPOCH,
                        enhet = listOf(Enhet(typeEnhet = "BOS", enhet = "8020", LocalDate.EPOCH)),
                        oppdragslinje = dto.utbetalingsperioder.map { Oppdragslinje.from(it, dto) },
                    )
                ),
            )

            fun from(dto: rest.SimuleringRequest): SimulerBeregningRequest =
                SimulerBeregningRequest(
                    request = SimulerRequest(
                        oppdrag = Oppdrag(
                            kodeFagomraade = dto.fagområde,
                            kodeEndring = if (dto.erFørsteUtbetalingPåSak) "NY" else "ENDR",
                            utbetFrekvens = dto.fagområde.utbetalingFrekvens(),
                            fagsystemId = dto.sakId,
                            oppdragGjelderId = dto.personident.verdi,
                            saksbehId = dto.saksbehandler,
                            datoOppdragGjelderFom = LocalDate.EPOCH,
                            enhet = listOf(Enhet(typeEnhet = "BOS", enhet = "8020", LocalDate.EPOCH)),
                            oppdragslinje = dto.utbetalingsperioder.map { Oppdragslinje.from(it, dto) },
                        )
                    ),
                )
        }
    }

    private fun FagsystemDto.utbetalingFrekvens() = when (this) {
        FagsystemDto.HISTORISK -> "ENG"
        else -> "MND"
    }

    private fun String.utbetalingFrekvens() = when (this) {
        "HELSREF" -> "ENG"
        else -> "MND"
    }

    @Serializable
    @XmlSerialName("request", "", "")
    data class SimulerRequest(
        val oppdrag: Oppdrag,
    )

    @Serializable
    @XmlSerialName("oppdrag", "", "")
    data class Oppdrag(
        val kodeEndring: String,
        val kodeFagomraade: String,
        val fagsystemId: String,
        val utbetFrekvens: String,
        val oppdragGjelderId: String,
        @Serializable(with = LocalDateSerializer::class)
        val datoOppdragGjelderFom: LocalDate?,
        val saksbehId: String,
        @XmlSerialName("enhet", "", "")
        val enhet: List<Enhet>,
        val oppdragslinje: List<Oppdragslinje>,
    )

    @Serializable
    @XmlSerialName("enhet", "", "")
    data class Enhet(
        val typeEnhet: String,
        val enhet: String,
        @Serializable(with = LocalDateSerializer::class)
        val datoEnhetFom: LocalDate?,
    )

    @Serializable
    @XmlSerialName("refusjonsInfo", "", "")
    data class RefusjonsInfo(
        val refunderesId: String,
        @Serializable(with = LocalDateSerializer::class)
        val datoFom: LocalDate,
        @Serializable(with = LocalDateSerializer::class)
        val maksDato: LocalDate?,
    )

    @Serializable
    @XmlSerialName("oppdragslinje", "", "")
    data class Oppdragslinje(
        val kodeEndringLinje: String,
        val kodeStatusLinje: KodeStatusLinje? = null,
        @Serializable(with = LocalDateSerializer::class)
        val datoStatusFom: LocalDate? = null,
        val delytelseId: String,
        val kodeKlassifik: String,
        @Serializable(with = LocalDateSerializer::class)
        val datoKlassifikFom: LocalDate,
        @Serializable(with = LocalDateSerializer::class)
        val datoVedtakFom: LocalDate,
        @Serializable(with = LocalDateSerializer::class)
        val datoVedtakTom: LocalDate,
        val sats: Int,
        val fradragTillegg: FradragTillegg,
        val typeSats: String,
        val brukKjoreplan: String,
        val saksbehId: String,
        val utbetalesTilId: String? = null,
        val refFagsystemId: String? = null,
        val refDelytelseId: String? = null,
        @XmlSerialName("attestant", "", "")
        val attestant: List<Attestant>,
        @XmlSerialName("vedtakssats", "", "")
        val vedtakssats: Vedtakssats? = null,
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
                    datoKlassifikFom = utbetalingsperiode.fom,
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
                    vedtakssats = null,
                )

            fun from(
                utbetalingsperiode: UtbetalingsperiodeDto,
                dto: UtbetalingsoppdragDto,
            ): Oppdragslinje =
                Oppdragslinje(
                    delytelseId = utbetalingsperiode.id,
                    refDelytelseId =
                        if (utbetalingsperiode.erEndringPåEksisterendePeriode) {
                            null
                        } else {
                            utbetalingsperiode.forrigePeriodeId
                        },
                    refFagsystemId =
                        if (utbetalingsperiode.erEndringPåEksisterendePeriode) {
                            null
                        } else {
                            utbetalingsperiode.forrigePeriodeId?.let { dto.saksnummer }
                        },
                    kodeEndringLinje = if (utbetalingsperiode.erEndringPåEksisterendePeriode) "ENDR" else "NY",
                    kodeKlassifik = utbetalingsperiode.klassekode,
                    datoKlassifikFom = utbetalingsperiode.fom,
                    kodeStatusLinje = utbetalingsperiode.opphør?.let { KodeStatusLinje.OPPH },
                    datoStatusFom = utbetalingsperiode.opphør?.fom,
                    datoVedtakFom = utbetalingsperiode.fom,
                    datoVedtakTom = utbetalingsperiode.tom,
                    sats = utbetalingsperiode.sats.toInt(),
                    fradragTillegg = FradragTillegg.T,
                    typeSats = utbetalingsperiode.satstype.value,
                    saksbehId = dto.saksbehandlerId,
                    brukKjoreplan = "N",
                    attestant = listOf(Attestant(dto.saksbehandlerId)),
                    utbetalesTilId = utbetalingsperiode.utbetalesTil,
                    vedtakssats = null,
                )
        }
    }

    @Serializable
    @XmlSerialName("fradragTillegg", "", "")
    enum class FradragTillegg {
        F,
        T,
    }

    @Serializable
    @XmlSerialName("kodeStatusLinje", "", "")
    enum class KodeStatusLinje {
        OPPH,
        HVIL,
        SPER,
        REAK,
    }

    @Serializable
    @XmlSerialName("attestant", "", "")
    data class Attestant(
        val attestantId: String,
    )

    @Serializable
    @XmlSerialName("vedtakssats", "", "")
    data class Vedtakssats(
        val vedtakssats: Int
    )
}
