package urskog.models

import com.fasterxml.jackson.annotation.JsonCreator
import java.util.UUID
import java.util.Base64
import java.nio.ByteBuffer
import java.time.*
import kotlin.getOrThrow

enum class Fagsystem {
    DP,
    TILTPENG,
    TILLST,
    AAP;

    companion object {
        fun from(stønad: Stønadstype) = when (stønad) {
            is StønadTypeDagpenger -> Fagsystem.DP
            is StønadTypeTiltakspenger -> Fagsystem.TILTPENG
            is StønadTypeTilleggsstønader -> Fagsystem.TILLST
            is StønadTypeAAP -> Fagsystem.AAP
        }
    }
}

@JvmInline
value class SakId(val id: String)

@JvmInline
value class BehandlingId(val id: String)

@JvmInline
value class UtbetalingId(val id: UUID) 

data class Utbetaling(
    val uid: UUID,
    val sakId: String,
    val behandlingId: String,
    val stønad: Stønadstype,
)

sealed interface Stønadstype {
    val name: String
    val klassekode: String

    companion object {
        @JsonCreator
        @JvmStatic
        fun valueOf(str: String): Stønadstype =
            runCatching { StønadTypeDagpenger.valueOf(str) }
                .recoverCatching { StønadTypeTilleggsstønader.valueOf(str) }
                .recoverCatching { StønadTypeTiltakspenger.valueOf(str) }
                .recoverCatching { StønadTypeAAP.valueOf(str) }
                .getOrThrow()
        }
}

enum class StønadTypeDagpenger(override val klassekode: String) : Stønadstype {
    ARBEIDSSØKER_ORDINÆR("DPORAS"),
    ARBEIDSSØKER_ORDINÆR_FERIETILLEGG("DPORASFE"),
    ARBEIDSSØKER_ORDINÆR_FERIETILLEGG_AVDØD("DPORASFE-IOP"),
    PERMITTERING_ORDINÆR("DPPEASFE1"),
    PERMITTERING_ORDINÆR_FERIETILLEGG("DPPEAS"),
    PERMITTERING_ORDINÆR_FERIETILLEGG_AVDØD("DPPEASFE1-IOP"),
    PERMITTERING_FISKEINDUSTRI("DPPEFIFE1"),
    PERMITTERING_FISKEINDUSTRI_FERIETILLEGG("DPPEFI"),
    PERMITTERING_FISKEINDUSTRI_FERIETILLEGG_AVDØD("DPPEFIFE1-IOP"),
    EØS("DPFEASISP"),
    EØS_FERIETILLEGG("DPDPASISP1");
}

// TODO: legg til klassekodene for barnetillegg
enum class StønadTypeTiltakspenger(override val klassekode: String) : Stønadstype {
    ARBEIDSFORBEREDENDE_TRENING("TPBTAF"),
    ARBEIDSRETTET_REHABILITERING("TPBTARREHABAGDAG"),
    ARBEIDSTRENING("TPBTATTILT"),
    AVKLARING("TPBTAAGR"),
    DIGITAL_JOBBKLUBB("TPBTDJK"),
    ENKELTPLASS_AMO("TPBTEPAMO"),
    ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG("TPBTEPVGSHOY"),
    FORSØK_OPPLÆRING_LENGRE_VARIGHET("TPBTFLV"),
    GRUPPE_AMO("TPBTGRAMO"),
    GRUPPE_VGS_OG_HØYERE_YRKESFAG("TPBTGRVGSHOY"),
    HØYERE_UTDANNING("TPBTHOYUTD"),
    INDIVIDUELL_JOBBSTØTTE("TPBTIPS"),
    INDIVIDUELL_KARRIERESTØTTE_UNG("TPBTIPSUNG"),
    JOBBKLUBB("TPBTJK2009"),
    OPPFØLGING("TPBTOPPFAGR"),
    UTVIDET_OPPFØLGING_I_NAV("TPBTUAOPPFL"),
    UTVIDET_OPPFØLGING_I_OPPLÆRING("TPBTUOPPFOPPL"),
    ARBEIDSFORBEREDENDE_TRENING_BARN("TPTPAFT"),
    ARBEIDSRETTET_REHABILITERING_BARN("TPTPARREHABAGDAG"),
    ARBEIDSTRENING_BARN("TPTPATT"),
    AVKLARING_BARN("TPTPAAG"),
    DIGITAL_JOBBKLUBB_BARN("TPTPDJB"),
    ENKELTPLASS_AMO_BARN("TPTPEPAMO"),
    ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG_BARN("TPTPEPVGSHOU"),
    FORSØK_OPPLÆRING_LENGRE_VARIGHET_BARN("TPTPFLV"),
    GRUPPE_AMO_BARN("TPTPGRAMO"),
    GRUPPE_VGS_OG_HØYERE_YRKESFAG_BARN("TPTPGRVGSHOY"),
    HØYERE_UTDANNING_BARN("TPTPHOYUTD"),
    INDIVIDUELL_JOBBSTØTTE_BARN("TPTPIPS"),
    INDIVIDUELL_KARRIERESTØTTE_UNG_BARN("TPTPIPSUNG"),
    JOBBKLUBB_BARN("TPTPJK2009"),
    OPPFØLGING_BARN("TPTPOPPFAG"),
    UTVIDET_OPPFØLGING_I_NAV_BARN("TPTPUAOPPF"),
    UTVIDET_OPPFØLGING_I_OPPLÆRING_BARN("TPTPUOPPFOPPL"),
}

enum class StønadTypeTilleggsstønader(override val klassekode: String) : Stønadstype {
    TILSYN_BARN_ENSLIG_FORSØRGER("TSTBASISP2-OP"),
    TILSYN_BARN_AAP("TSTBASISP4-OP"),
    TILSYN_BARN_ETTERLATTE("TSTBASISP5-OP"),
    LÆREMIDLER_ENSLIG_FORSØRGER("TSLMASISP2-OP"),
    LÆREMIDLER_AAP("TSLMASISP3-OP"),
    LÆREMIDLER_ETTERLATTE("TSLMASISP4-OP"),
}

enum class StønadTypeAAP(override val klassekode: String) : Stønadstype {
    AAP_UNDER_ARBEIDSAVKLARING("AAPUAA"),
}
