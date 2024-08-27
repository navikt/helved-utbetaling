package utsjekk.iverksetting

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import no.nav.utsjekk.kontrakter.felles.*
import no.nav.utsjekk.kontrakter.iverksett.Ferietillegg
import no.nav.utsjekk.kontrakter.oppdrag.Utbetalingsoppdrag
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.random.Random

data class Iverksetting(
    val fagsak: Fagsakdetaljer,
    val behandling: Behandlingsdetaljer,
    val søker: Søker,
    val vedtak: Vedtaksdetaljer,
) {
    companion object;

    override fun toString() =
        "fagsystem ${fagsak.fagsystem}, sak $sakId, behandling $behandlingId, iverksettingId ${behandling.iverksettingId}"
}

data class Fagsakdetaljer(
    val fagsakId: SakId,
    val fagsystem: Fagsystem,
)

@JvmInline
value class Søker(
    val personident: String,
)

data class Vedtaksdetaljer(
    val vedtakstidspunkt: LocalDateTime,
    val saksbehandlerId: String,
    val beslutterId: String,
    val brukersNavKontor: BrukersNavKontor? = null,
    val tilkjentYtelse: TilkjentYtelse,
)

data class Behandlingsdetaljer(
    val forrigeBehandlingId: BehandlingId? = null,
    val forrigeIverksettingId: IverksettingId? = null,
    val behandlingId: BehandlingId,
    val iverksettingId: IverksettingId? = null,
)

val Iverksetting.sakId get() = this.fagsak.fagsakId
val Iverksetting.personident get() = this.søker.personident

val Iverksetting.behandlingId get() = this.behandling.behandlingId
val Iverksetting.iverksettingId get() = this.behandling.iverksettingId

data class TilkjentYtelse(
    val id: String = RandomOSURId.generate(),
    val utbetalingsoppdrag: Utbetalingsoppdrag? = null,
    val andelerTilkjentYtelse: List<AndelTilkjentYtelse>,
    val sisteAndelIKjede: AndelTilkjentYtelse? = null,
    @JsonSerialize(keyUsing = KjedenøkkelKeySerializer::class)
    @JsonDeserialize(keyUsing = KjedenøkkelKeyDeserializer::class)
    val sisteAndelPerKjede: Map<Stønadsdata, AndelTilkjentYtelse> =
        sisteAndelIKjede?.let {
            mapOf(it.stønadsdata to it)
        } ?: emptyMap(),
) {
    companion object Mapper
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes(
    JsonSubTypes.Type(StønadsdataDagpenger::class, name = "dagpenger"),
    JsonSubTypes.Type(StønadsdataTiltakspenger::class, name = "tiltakspenger"),
    JsonSubTypes.Type(StønadsdataTilleggsstønader::class, name = "tilleggsstønader"),
)
sealed class Stønadsdata(open val stønadstype: StønadType) {
    companion object;

    fun tilKlassifisering(): String =
        when (this) {
            is StønadsdataDagpenger -> this.tilKlassifiseringDagpenger()
            is StønadsdataTiltakspenger -> this.tilKlassifiseringTiltakspenger()
            is StønadsdataTilleggsstønader -> this.tilKlassifiseringTilleggsstønader()
        }
}

data class StønadsdataDagpenger(override val stønadstype: StønadTypeDagpenger, val ferietillegg: Ferietillegg? = null) :
    Stønadsdata(stønadstype) {
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
}

data class StønadsdataTiltakspenger(
    override val stønadstype: StønadTypeTiltakspenger,
    val barnetillegg: Boolean = false,
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
        }
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

    companion object;
}

data class Periode(val fom: LocalDate, val tom: LocalDate) : Comparable<Periode> {
    init {
        require(tom >= fom) { "Tom-dato $tom før fom-dato $fom er ugyldig" }
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
