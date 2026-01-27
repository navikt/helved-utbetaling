package utsjekk.iverksetting

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.KeyDeserializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.module.kotlin.readValue
import libs.utils.appLog
import models.DocumentedErrors
import models.badRequest
import models.kontrakter.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.random.Random

@JvmInline
value class SakId(val id: String)

@JvmInline
value class BehandlingId(val id: String)

@JvmInline
value class IverksettingId(val id: String)

data class Iverksetting(
    val behandling: Behandlingsdetaljer,
    val fagsak: Fagsakdetaljer,
    val søker: Søker,
    val vedtak: Vedtaksdetaljer,
) {
    override fun toString() =
        "fagsystem ${fagsak.fagsystem}, sak $sakId, behandling $behandlingId, iverksettingId ${behandling.iverksettingId}"

    companion object {
        fun from(dto: IverksettV2Dto, fagsystem: Fagsystem): Iverksetting {
            return Iverksetting(
                fagsak = Fagsakdetaljer(SakId(dto.sakId), fagsystem),
                søker = Søker(personident = dto.personident.verdi),
                behandling = Behandlingsdetaljer.from(dto),
                vedtak = Vedtaksdetaljer.from(dto.vedtak)
            )
        }
    }
}

data class UtbetalingId(
    val fagsystem: Fagsystem,
    val sakId: SakId,
    val behandlingId: BehandlingId,
    val iverksettingId: IverksettingId?
)

/**
 * @param andeler er alle andeler med nye periodeId/forrigePeriodeId for å kunne oppdatere lagrede andeler
 */
data class BeregnetUtbetalingsoppdrag(
    val utbetalingsoppdrag: Utbetalingsoppdrag,
    val andeler: List<AndelMedPeriodeId>,
)

data class AndelMedPeriodeId(
    val id: String,
    val periodeId: Long,
    val forrigePeriodeId: Long?,
) {
    constructor(andel: AndelData) :
            this(
                id = andel.id,
                periodeId = andel.periodeId ?: error("Mangler offset på andel=${andel.id}"),
                forrigePeriodeId = andel.forrigePeriodeId,
            )
}

data class Behandlingsinformasjon(
    val saksbehandlerId: String,
    val beslutterId: String,
    val fagsakId: SakId,
    val fagsystem: Fagsystem,
    val behandlingId: BehandlingId,
    val personident: String,
    val vedtaksdato: LocalDate,
    val brukersNavKontor: BrukersNavKontor? = null,
    val iverksettingId: IverksettingId?,
)

data class ResultatForKjede(
    val beståendeAndeler: List<AndelData>,
    val nyeAndeler: List<AndelData>,
    val opphørsandel: Pair<AndelData, LocalDate>?,
    val sistePeriodeId: Long,
)

data class Fagsakdetaljer(
    val fagsakId: SakId,
    val fagsystem: Fagsystem,
)

data class Søker(
    val personident: String,
)

data class Vedtaksdetaljer(
    val vedtakstidspunkt: LocalDateTime,
    val saksbehandlerId: String,
    val beslutterId: String,
    val brukersNavKontor: BrukersNavKontor? = null,
    val tilkjentYtelse: TilkjentYtelse,
) {
    companion object {
        fun from(dto: VedtaksdetaljerV2Dto) = Vedtaksdetaljer(
            vedtakstidspunkt = dto.vedtakstidspunkt,
            saksbehandlerId = dto.saksbehandlerId,
            beslutterId = dto.beslutterId,
            tilkjentYtelse = TilkjentYtelse.from(dto.utbetalinger)
        )
    }
}

data class Behandlingsdetaljer(
    val forrigeBehandlingId: BehandlingId? = null,
    val forrigeIverksettingId: IverksettingId? = null,
    val behandlingId: BehandlingId,
    val iverksettingId: IverksettingId? = null,
) {
    companion object {
        fun from(dto: IverksettV2Dto) = Behandlingsdetaljer(
            behandlingId = BehandlingId(dto.behandlingId),
            forrigeBehandlingId = dto.forrigeIverksetting?.behandlingId?.let(::BehandlingId),
            iverksettingId = dto.iverksettingId?.let(::IverksettingId),
            forrigeIverksettingId = dto.forrigeIverksetting?.iverksettingId?.let(::IverksettingId)
        )
    }
}

val Iverksetting.sakId get() = this.fagsak.fagsakId
val Iverksetting.personident get() = this.søker.personident
val Iverksetting.behandlingId get() = this.behandling.behandlingId
val Iverksetting.iverksettingId get() = this.behandling.iverksettingId

data class TilkjentYtelse(
    val id: String = RandomOSURId.generate(),
    val utbetalingsoppdrag: Utbetalingsoppdrag? = null,
    val andelerTilkjentYtelse: List<AndelTilkjentYtelse>,
    val sisteAndelIKjede: AndelTilkjentYtelse? = null,
    @param:JsonSerialize(keyUsing = KjedenøkkelKeySerializer::class)
    @param:JsonDeserialize(keyUsing = KjedenøkkelKeyDeserializer::class)
    val sisteAndelPerKjede: Map<Kjedenøkkel, AndelTilkjentYtelse> =
        sisteAndelIKjede?.let {
            mapOf(it.stønadsdata.tilKjedenøkkel() to it)
        } ?: emptyMap(),
) {
    companion object {
        fun from(dtos: List<UtbetalingV2Dto>): TilkjentYtelse {
            val andeler = dtos.map(AndelTilkjentYtelse::from)

            return when (andeler.size) {
                0 -> TilkjentYtelse(andelerTilkjentYtelse = emptyList())
                else -> TilkjentYtelse(andelerTilkjentYtelse = andeler)
            }
        }

        fun from(json: String): TilkjentYtelse {
            appLog.debug("trying to deserialize TilkjentYtelse: $json")
            return objectMapper.readValue(json)
        }
    }

    fun toJson(): String = objectMapper.writeValueAsString(this)
}

data class OppdragResultat(
    val oppdragStatus: OppdragStatus,
    val oppdragStatusOppdatert: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        fun from(json: String): OppdragResultat {
            appLog.debug("trying to deserialize OppdragResultat: $json")
            return objectMapper.readValue(json)
        }
    }

    fun toJson(): String = objectMapper.writeValueAsString(this)
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes(
    JsonSubTypes.Type(StønadsdataAAP::class, name = "aap"),
    JsonSubTypes.Type(StønadsdataDagpenger::class, name = "dagpenger"),
    JsonSubTypes.Type(StønadsdataTiltakspenger::class, name = "tiltakspenger"),
    JsonSubTypes.Type(StønadsdataTilleggsstønader::class, name = "tilleggsstønader"),
)
sealed class Stønadsdata(open val stønadstype: StønadType) {
    companion object {
        fun from(dto: StønadsdataDto): Stønadsdata {
            return when (dto) {
                is StønadsdataAAPDto -> StønadsdataAAP(
                    stønadstype = dto.stønadstype,
                    fastsattDagsats = dto.fastsattDagsats
                )
                is StønadsdataDagpengerDto -> StønadsdataDagpenger(
                    stønadstype = dto.stønadstype,
                    ferietillegg = dto.ferietillegg,
                    meldekortId = dto.meldekortId,
                    fastsattDagsats = dto.fastsattDagsats,
                )
                is StønadsdataTiltakspengerV2Dto -> StønadsdataTiltakspenger(
                    stønadstype = dto.stønadstype,
                    barnetillegg = dto.barnetillegg,
                    brukersNavKontor = BrukersNavKontor(enhet = dto.brukersNavKontor),
                    meldekortId = dto.meldekortId,
                )
                is StønadsdataTilleggsstønaderDto -> StønadsdataTilleggsstønader(
                    stønadstype = dto.stønadstype,
                    brukersNavKontor = dto.brukersNavKontor?.let(::BrukersNavKontor)
                )
            }
        }
    }

    fun tilKlassifisering(): String =
        when (this) {
            is StønadsdataAAP -> this.tilKlassifiseringAAP()
            is StønadsdataDagpenger -> this.tilKlassifiseringDagpenger()
            is StønadsdataTiltakspenger -> this.tilKlassifiseringTiltakspenger()
            is StønadsdataTilleggsstønader -> this.tilKlassifiseringTilleggsstønader()
        }

    abstract fun tilKjedenøkkel(): Kjedenøkkel
}

data class StønadsdataDagpenger(
    override val stønadstype: StønadTypeDagpenger,
    val ferietillegg: Ferietillegg? = null,
    val meldekortId: String,
    val fastsattDagsats: UInt,
) : Stønadsdata(stønadstype) {
    fun tilKlassifiseringDagpenger(): String =
        when (this.stønadstype) {
            StønadTypeDagpenger.DAGPENGER_ARBEIDSSØKER_ORDINÆR ->
                when (ferietillegg) {
                    Ferietillegg.ORDINÆR -> "DPORASFE"
                    Ferietillegg.AVDØD -> "DPORASFE-IOP"
                    null -> "DPORAS"
                }

            StønadTypeDagpenger.DAGPENGER_PERMITTERING_ORDINÆR ->
                when (ferietillegg) {
                    Ferietillegg.ORDINÆR -> "DPPEASFE1"
                    Ferietillegg.AVDØD -> "DPPEASFE1-IOP"
                    null -> "DPPEAS"
                }

            StønadTypeDagpenger.DAGPENGER_PERMITTERING_FISKEINDUSTRI ->
                when (ferietillegg) {
                    Ferietillegg.ORDINÆR -> "DPPEFIFE1"
                    Ferietillegg.AVDØD -> "DPPEFIFE1-IOP"
                    null -> "DPPEFI"
                }

            StønadTypeDagpenger.DAGPENGER_EØS ->
                when (ferietillegg) {
                    Ferietillegg.ORDINÆR -> "DPFEASISP"
                    Ferietillegg.AVDØD -> throw IllegalArgumentException("Eksport-gruppen har ingen egen kode for ferietillegg til avdød")
                    null -> "DPDPASISP1"
                }
        }

    override fun tilKjedenøkkel(): Kjedenøkkel =
        KjedenøkkelMeldeplikt(klassifiseringskode = this.tilKlassifiseringDagpenger(), meldekortId = this.meldekortId)
}

data class StønadsdataTiltakspenger(
    override val stønadstype: StønadTypeTiltakspenger,
    val barnetillegg: Boolean = false,
    val brukersNavKontor: BrukersNavKontor,
    val meldekortId: String,
) : Stønadsdata(stønadstype) {
    fun tilKlassifiseringTiltakspenger(): String =
        if (barnetillegg) {
            when (this.stønadstype) {
                StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING -> "TPBTAF"
                StønadTypeTiltakspenger.ARBEIDSRETTET_REHABILITERING -> "TPBTARREHABAGDAG"
                StønadTypeTiltakspenger.ARBEIDSTRENING -> "TPBTATTILT"
                StønadTypeTiltakspenger.AVKLARING -> "TPBTAAGR"
                StønadTypeTiltakspenger.DIGITAL_JOBBKLUBB -> "TPBTDJK"
                StønadTypeTiltakspenger.ENKELTPLASS_AMO -> "TPBTEPAMO"
                StønadTypeTiltakspenger.ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG -> "TPBTEPVGSHOY"
                StønadTypeTiltakspenger.FORSØK_OPPLÆRING_LENGRE_VARIGHET -> "TPBTFLV"
                StønadTypeTiltakspenger.GRUPPE_AMO -> "TPBTGRAMO"
                StønadTypeTiltakspenger.GRUPPE_VGS_OG_HØYERE_YRKESFAG -> "TPBTGRVGSHOY"
                StønadTypeTiltakspenger.HØYERE_UTDANNING -> "TPBTHOYUTD"
                StønadTypeTiltakspenger.INDIVIDUELL_JOBBSTØTTE -> "TPBTIPS"
                StønadTypeTiltakspenger.INDIVIDUELL_KARRIERESTØTTE_UNG -> "TPBTIPSUNG"
                StønadTypeTiltakspenger.JOBBKLUBB -> "TPBTJK2009"
                StønadTypeTiltakspenger.OPPFØLGING -> "TPBTOPPFAGR"
                StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_NAV -> "TPBTUAOPPFL"
                StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_OPPLÆRING -> "TPBTUOPPFOPPL"
            }
        } else {
            when (this.stønadstype) {
                StønadTypeTiltakspenger.ARBEIDSFORBEREDENDE_TRENING -> "TPTPAFT"
                StønadTypeTiltakspenger.ARBEIDSRETTET_REHABILITERING -> "TPTPARREHABAGDAG"
                StønadTypeTiltakspenger.ARBEIDSTRENING -> "TPTPATT"
                StønadTypeTiltakspenger.AVKLARING -> "TPTPAAG"
                StønadTypeTiltakspenger.DIGITAL_JOBBKLUBB -> "TPTPDJB"
                StønadTypeTiltakspenger.ENKELTPLASS_AMO -> "TPTPEPAMO"
                StønadTypeTiltakspenger.ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG -> "TPTPEPVGSHOU"
                StønadTypeTiltakspenger.FORSØK_OPPLÆRING_LENGRE_VARIGHET -> "TPTPFLV"
                StønadTypeTiltakspenger.GRUPPE_AMO -> "TPTPGRAMO"
                StønadTypeTiltakspenger.GRUPPE_VGS_OG_HØYERE_YRKESFAG -> "TPTPGRVGSHOY"
                StønadTypeTiltakspenger.HØYERE_UTDANNING -> "TPTPHOYUTD"
                StønadTypeTiltakspenger.INDIVIDUELL_JOBBSTØTTE -> "TPTPIPS"
                StønadTypeTiltakspenger.INDIVIDUELL_KARRIERESTØTTE_UNG -> "TPTPIPSUNG"
                StønadTypeTiltakspenger.JOBBKLUBB -> "TPTPJK2009"
                StønadTypeTiltakspenger.OPPFØLGING -> "TPTPOPPFAG"
                StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_NAV -> "TPTPUAOPPF"
                StønadTypeTiltakspenger.UTVIDET_OPPFØLGING_I_OPPLÆRING -> "TPTPUOPPFOPPL"
            }
        }

    override fun tilKjedenøkkel(): Kjedenøkkel =
        KjedenøkkelMeldeplikt(
            klassifiseringskode = this.tilKlassifiseringTiltakspenger(),
            meldekortId = this.meldekortId
        )
}

data class StønadsdataTilleggsstønader(
    override val stønadstype: StønadTypeTilleggsstønader,
    val brukersNavKontor: BrukersNavKontor? = null,
) : Stønadsdata(stønadstype) {
    fun tilKlassifiseringTilleggsstønader() =
        when (stønadstype) {
            StønadTypeTilleggsstønader.TILSYN_BARN_ENSLIG_FORSØRGER -> "TSTBASISP2-OP"
            StønadTypeTilleggsstønader.TILSYN_BARN_AAP -> "TSTBASISP4-OP"
            StønadTypeTilleggsstønader.TILSYN_BARN_ETTERLATTE -> "TSTBASISP5-OP"
            StønadTypeTilleggsstønader.LÆREMIDLER_ENSLIG_FORSØRGER -> "TSLMASISP2-OP"
            StønadTypeTilleggsstønader.LÆREMIDLER_AAP -> "TSLMASISP3-OP"
            StønadTypeTilleggsstønader.LÆREMIDLER_ETTERLATTE -> "TSLMASISP4-OP"
            StønadTypeTilleggsstønader.BOUTGIFTER_AAP -> "TSBUASIA-OP"
            StønadTypeTilleggsstønader.BOUTGIFTER_ENSLIG_FORSØRGER -> "TSBUAISP2-OP"
            StønadTypeTilleggsstønader.BOUTGIFTER_ETTERLATTE -> "TSBUAISP3-O"
        }

    override fun tilKjedenøkkel(): Kjedenøkkel =
        KjedenøkkelStandard(klassifiseringskode = this.tilKlassifiseringTilleggsstønader())
}

data class StønadsdataAAP(
    override val stønadstype: StønadTypeAAP,
    val fastsattDagsats: UInt? = null,
) : Stønadsdata(stønadstype) {
    fun tilKlassifiseringAAP() =
        when (stønadstype) {
            StønadTypeAAP.AAP_UNDER_ARBEIDSAVKLARING -> "AAPOR"
        }

    override fun tilKjedenøkkel(): Kjedenøkkel =
        KjedenøkkelStandard(klassifiseringskode = this.tilKlassifiseringAAP())
}

data class AndelTilkjentYtelse(
    val beløp: Int,
    val satstype: Satstype = Satstype.DAGLIG,
    val periode: Periode,
    val stønadsdata: Stønadsdata,
    val periodeId: Long? = null,
    val forrigePeriodeId: Long? = null,
) {
    var id: UUID = UUID.randomUUID()

    companion object {
        fun from(dto: UtbetalingV2Dto) = AndelTilkjentYtelse(
            beløp = dto.beløp.toInt(),
            satstype = dto.satstype,
            periode = Periode(dto.fraOgMedDato, dto.tilOgMedDato),
            stønadsdata = Stønadsdata.from(dto.stønadsdata)
        )
    }
}

data class Periode(val fom: LocalDate, val tom: LocalDate) : Comparable<Periode> {
    init {
        if (tom < fom) badRequest(DocumentedErrors.Async.Utbetaling.UGYLDIG_PERIODE)
    }

    override fun compareTo(other: Periode): Int {
        return Comparator.comparing(Periode::fom).thenComparing(Periode::tom).compare(this, other)
    }
}

fun AndelTilkjentYtelse.tilAndelData() =
    AndelData(
        id = this.id.toString(),
        fom = this.periode.fom,
        tom = this.periode.tom,
        beløp = this.beløp,
        satstype = this.satstype,
        stønadsdata = this.stønadsdata,
        periodeId = this.periodeId,
        forrigePeriodeId = this.forrigePeriodeId,
    )

fun TilkjentYtelse?.lagAndelData(): List<AndelData> =
    this?.andelerTilkjentYtelse?.map {
        it.tilAndelData()
    } ?: emptyList()

object RandomOSURId {
    private val chars: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    fun generate() = (1..20).map { Random.nextInt(0, chars.size).let { chars[it] } }.joinToString("")
}

/**
 * ID her burde ikke brukes til noe spesielt. EF har ikke et ID på andeler som sendes til utbetalingsgeneratorn
 */
data class AndelData(
    val id: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val beløp: Int,
    val satstype: Satstype = Satstype.DAGLIG,
    val stønadsdata: Stønadsdata,
    val periodeId: Long? = null,
    val forrigePeriodeId: Long? = null,
)

internal fun List<AndelData>.uten0beløp(): List<AndelData> = this.filter { it.beløp != 0 }

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes(
    JsonSubTypes.Type(KjedenøkkelMeldeplikt::class, name = "meldeplikt"),
    JsonSubTypes.Type(KjedenøkkelStandard::class, name = "standard"),
)
sealed class Kjedenøkkel(
    open val klassifiseringskode: String,
)

data class KjedenøkkelMeldeplikt(
    override val klassifiseringskode: String,
    val meldekortId: String,
) : Kjedenøkkel(klassifiseringskode)

data class KjedenøkkelStandard(
    override val klassifiseringskode: String,
) : Kjedenøkkel(klassifiseringskode)

class KjedenøkkelKeySerializer : JsonSerializer<Kjedenøkkel>() {
    override fun serialize(
        value: Kjedenøkkel?,
        gen: JsonGenerator?,
        serializers: SerializerProvider?,
    ) {
        gen?.let { jGen ->
            value?.let { kjedenøkkel ->
                jGen.writeFieldName(objectMapper.writeValueAsString(kjedenøkkel))
            } ?: jGen.writeNull()
        }
    }
}

class KjedenøkkelKeyDeserializer : KeyDeserializer() {
    override fun deserializeKey(
        key: String?,
        ctx: DeserializationContext?,
    ): Kjedenøkkel? = key?.let { objectMapper.readValue(key, Kjedenøkkel::class.java) }
}

enum class OppdragStatus {
    LAGT_PÅ_KØ,
    KVITTERT_OK,
    KVITTERT_MED_MANGLER,
    KVITTERT_FUNKSJONELL_FEIL,
    KVITTERT_TEKNISK_FEIL,
    KVITTERT_UKJENT,
    OK_UTEN_UTBETALING,
}

data class Utbetalingsoppdrag(
    val erFørsteUtbetalingPåSak: Boolean,
    val fagsystem: Fagsystem,
    val saksnummer: String,
    val iverksettingId: String?,
    val aktør: String,
    val saksbehandlerId: String,
    val beslutterId: String? = null,
    val avstemmingstidspunkt: LocalDateTime = LocalDateTime.now(),
    val utbetalingsperiode: List<Utbetalingsperiode>,
    val brukersNavKontor: String? = null,
)

data class Utbetalingsperiode(
    val erEndringPåEksisterendePeriode: Boolean,
    val opphør: Opphør? = null,
    val periodeId: Long,
    val forrigePeriodeId: Long? = null,
    val vedtaksdato: LocalDate,
    val klassifisering: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val sats: BigDecimal,
    val satstype: Satstype,
    val utbetalesTil: String,
    val behandlingId: String,
    val utbetalingsgrad: Int? = null,
    val fastsattDagsats: BigDecimal? = null,
)

data class Opphør(val fom: LocalDate)

