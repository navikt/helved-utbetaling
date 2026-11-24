@file:UseSerializers(DecimalSerializer::class, IntegerSerializer::class)

package simulering

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.BigInteger

// Wire models for sokos-oppdrag's osSimulerBeregningApi, not the legacy upstream REST API.
@Serializable
data class SimuleringRequest(
    @SerialName("OsSimulerBeregningOperation") val operation: SimuleringOperation
)

@Serializable
data class SimuleringOperation(@SerialName("ZS1ICont") val input: SimuleringInput)

@Serializable
data class SimuleringInput(val requestSimulering: RequestSimulering)

@Serializable
data class RequestSimulering(
    val oppdrag: Oppdrag,
    val simuleringsPeriode: Simuleringsperiode? = null,
    val bilagstype: List<Bilagstype> = emptyList(),
    val avstemmingsnokkel: List<Avstemmingsnokkel> = emptyList(),
    val omposter: List<Omposter> = emptyList(),
    val enhetOppdrag: List<Oppdragsenhet> = emptyList(),
    val tekstOppdrag: List<Tekst> = emptyList(),
    val oppdragslinje: List<Oppdragslinje> = emptyList()
)

@Serializable
data class Oppdrag(
    val kodeEndring: String?,
    val kodeStatus: String?,
    val datoStatusFom: String?,
    val kodeFagomraade: String,
    val fagsystemId: String,
    val oppdragsId: Long?,
    val utbetFrekvens: String?,
    val datoForfall: String?,
    val stonadId: String?,
    val oppdragGjelderId: String,
    val datoOppdragGjelderFom: String?,
    val saksbehId: String?
)

@Serializable
data class Simuleringsperiode(val datoSimulerFom: String?, val datoSimulerTom: String?)

@Serializable
data class Bilagstype(val typeBilag: String)

@Serializable
data class Avstemmingsnokkel(val kodeKomponent: String?, val nokkelAvstemming: String?, val tidspktMelding: String?)

@Serializable
data class Omposter(val ompostering: String?, val datoOmposterFom: String?, val feilreg: Boolean?)

@Serializable
data class Oppdragsenhet(val typeEnhet: String?, val enhet: String?, val datoEnhetFom: String?)

@Serializable
data class Tekst(
    val tekstLnr: BigInteger?,
    val tekstKode: String?,
    val tekst: String?,
    val datoTekstFom: String?,
    val datoTekstTom: String?,
    val feilreg: Boolean?
)

@Serializable
data class Oppdragslinje(
    val kodeEndringLinje: String?,
    val kodeStatusLinje: String?,
    val datoStatusFom: String?,
    val vedtakId: String?,
    val delytelseId: String?,
    val linjeId: BigInteger?,
    val kodeKlassifik: String?,
    val datoKlassifikFom: String?,
    val datoVedtakFom: String?,
    val datoVedtakTom: String?,
    val sats: BigDecimal?,
    val typeSats: String?,
    val skyldnerId: String?,
    val datoSkyldnerFom: String?,
    val kravhaverId: String?,
    val datoKravhaverFom: String?,
    val kid: String?,
    val datoKidFom: String?,
    val brukKjoreplan: Boolean?,
    val saksbehId: String?,
    val utbetalesTilId: String?,
    val datoUtbetalesTilIdFom: String?,
    val kodeArbeidsgiver: String?,
    val henvisning: String?,
    val typeSoknad: String?,
    val refFagsystemId: String?,
    val refOppdragsId: Long?,
    val refDelytelseId: String?,
    val refLinjeId: BigInteger?,
    val refusjonsInfo: List<RefusjonsInfo>,
    val tekstOppdragslinje: List<Tekst>,
    val enhetOppdragslinje: List<Oppdragsenhet>,
    val gradOppdragslinje: List<Grad>,
    val attestant: List<Attestant>,
    @SerialName("valutaOppdagslinje") val valutaOppdragslinje: List<Valuta>
)

@Serializable
data class RefusjonsInfo(val refunderesId: String?, val maksDato: String?, val datoFom: String?)

@Serializable
data class Grad(val typeGrad: String?, val grad: BigInteger?)

@Serializable
data class Attestant(val attestantId: String?, val datoUgyldigFom: String?)

@Serializable
data class Valuta(val typeValuta: String?, val valuta: String?, val datoValutaFom: String?, val feilreg: Boolean?)

@Serializable
data class SimuleringResponse(
    @SerialName("OsSimulerBeregningOperationResponse") val operation: SimuleringOperationResponse
)

@Serializable
data class SimuleringOperationResponse(@SerialName("ZS1OCont") val output: SimuleringOutput)

@Serializable
data class SimuleringOutput(val responsSimulering: ResponsSimulering)

@Serializable
data class ResponsSimulering(val statusKode: Int, val statusMelding: String? = null, val beregning: RestBeregning? = null)

@Serializable
data class RestBeregning(
    val gjelderId: String,
    val datoBeregnet: String,
    val belopTotalt: BigDecimal,
    val periodeStoppnivaa: List<PeriodeStoppnivaa> = emptyList()
)

@Serializable
data class PeriodeStoppnivaa(val beregningsPeriode: RestBeregningsperiode, val beregningStoppnivaa: RestStoppnivaa)

@Serializable
data class RestBeregningsperiode(val periodeFom: String, val periodeTom: String)

@Serializable
data class RestStoppnivaa(
    val kodeFagomraade: String,
    val fagsystemId: String,
    val utbetalesTilId: String,
    val stoppnivaaDetaljer: List<RestPostering> = emptyList()
)

@Serializable
data class RestPostering(
    val faktiskFom: String,
    val faktiskTom: String,
    val belop: BigDecimal,
    val klassekode: String,
    val typeKlasse: String
)

object DecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.DOUBLE)
    override fun serialize(encoder: Encoder, value: BigDecimal) = (encoder as JsonEncoder).encodeJsonElement(JsonPrimitive(value))
    override fun deserialize(decoder: Decoder): BigDecimal = (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content.toBigDecimal()
}

object IntegerSerializer : KSerializer<BigInteger> {
    override val descriptor = PrimitiveSerialDescriptor("BigInteger", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: BigInteger) = (encoder as JsonEncoder).encodeJsonElement(JsonPrimitive(value))
    override fun deserialize(decoder: Decoder): BigInteger = (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content.toBigInteger()
}
