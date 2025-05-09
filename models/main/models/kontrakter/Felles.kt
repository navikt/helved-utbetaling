package models.kontrakter.felles

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.YearMonthDeserializer
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.lang.IllegalArgumentException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class BrukersNavKontor(
    val enhet: String,
    val gjelderFom: LocalDate? = null,
)

enum class Fagsystem(val kode: String) {
    AAP("AAP"),
    DAGPENGER("DP"),
    TILTAKSPENGER("TILTPENG"),
    TILLEGGSSTØNADER("TILLST"),
}

fun String.tilFagsystem(): Fagsystem = Fagsystem.values().find { it.kode == this } ?: throw IllegalArgumentException("$this er ukjent fagsystem")

object GyldigSakId {
    const val MAKSLENGDE = 25
    const val BESKRIVELSE = "På grunn av tekniske begrensninger hos OS/UR er det en lengdebegrensning på $MAKSLENGDE tegn for sakId"
}

object GyldigBehandlingId {
    const val MAKSLENGDE = 30
    const val BESKRIVELSE = "På grunn av tekniske begrensninger hos OS/UR er det en lengdebegrensning på $MAKSLENGDE tegn for behandlingId"
}

sealed class Ident(val verdi: String) {
    companion object {
        @JvmStatic
        @JsonCreator
        fun deserialize(json: String) =
            when (json.length) {
                9 -> Organisasjonsnummer(json)
                11 -> Personident(json)
                else -> throw Exception("Ugyldig ident")
            }

        @JvmStatic
        @JsonCreator
        fun deserializeObject(json: JsonNode) = deserialize(json.get("verdi").asText())
    }
}

class Organisasjonsnummer(verdi: String) : Ident(verdi) {
    init {
        check(gyldig()) { "Organisasjonsnummeret er ugyldig" }
    }

    companion object {
        @JvmStatic
        @JsonCreator
        fun deserialize(json: String) = Organisasjonsnummer(json)

        @JvmStatic
        @JsonCreator
        fun deserializeObject(json: JsonNode) = Organisasjonsnummer(json.get("verdi").asText())
    }

    private fun gyldig(): Boolean {
        val sifre = verdi.chunked(1).map(String::toInt)
        val vekttall = intArrayOf(3, 2, 7, 6, 5, 4, 3, 2)
        val kontrolltall = 11 - (0..7).sumOf { vekttall[it] * sifre[it] } % 11
        return kontrolltall == sifre.last() || kontrolltall == 11 && sifre.last() == 0
    }
}

class Personident(verdi: String) : Ident(verdi) {
    init {
        check(gyldig()) { "Personidenten er ugyldig" }
    }

    companion object {
        @JvmStatic
        @JsonCreator
        fun deserialize(json: String) = Personident(json)

        @JvmStatic
        @JsonCreator
        fun deserializeObject(json: JsonNode) = Personident(json.get("verdi").asText())
    }

    private fun gyldig(): Boolean {
        if (verdi.length != 11 || verdi.toLongOrNull() == null) {
            return false
        }

        val siffer = verdi.chunked(1).map { it.toInt() }
        val k1Vekting = intArrayOf(3, 7, 6, 1, 8, 9, 4, 5, 2)
        val k2Vekting = intArrayOf(5, 4, 3, 2, 7, 6, 5, 4, 3, 2)

        val kontrollMod1 = 11 - (0..8).sumOf { k1Vekting[it] * siffer[it] } % 11
        val kontrollMod2 = 11 - (0..9).sumOf { k2Vekting[it] * siffer[it] } % 11
        val kontrollsiffer1 = siffer[9]
        val kontrollsiffer2 = siffer[10]

        return gyldigKontrollSiffer(kontrollMod1, kontrollsiffer1) && gyldigKontrollSiffer(kontrollMod2, kontrollsiffer2)
    }

    private fun gyldigKontrollSiffer(kontrollMod: Int, kontrollsiffer: Int): Boolean {
        if (kontrollMod == kontrollsiffer) {
            return true
        }
        if (kontrollMod == 11 && kontrollsiffer == 0) {
            return true
        }
        return false
    }
}

enum class Satstype {
    DAGLIG,
    DAGLIG_INKL_HELG,
    MÅNEDLIG,
    ENGANGS,
}

sealed interface StønadType {
    val name: String
    fun tilFagsystem(): Fagsystem

    companion object {
        @JsonCreator
        @JvmStatic
        fun deserialize(json: String): StønadType? {
            val stønadstypeDagpenger = StønadTypeDagpenger.values().find { it.name == json }
            val stønadstypeTiltakspenger = StønadTypeTiltakspenger.values().find { it.name == json }
            val stønadstypeTilleggsstønader = StønadTypeTilleggsstønader.values().find { it.name == json }
            return stønadstypeDagpenger ?: stønadstypeTiltakspenger ?: stønadstypeTilleggsstønader
        }
    }
}

enum class StønadTypeDagpenger : StønadType {
    DAGPENGER_ARBEIDSSØKER_ORDINÆR,
    DAGPENGER_PERMITTERING_ORDINÆR,
    DAGPENGER_PERMITTERING_FISKEINDUSTRI,
    DAGPENGER_EØS;

    override fun tilFagsystem(): Fagsystem = Fagsystem.DAGPENGER
}

enum class StønadTypeTiltakspenger : StønadType {
    ARBEIDSFORBEREDENDE_TRENING,
    ARBEIDSRETTET_REHABILITERING,
    ARBEIDSTRENING,
    AVKLARING,
    DIGITAL_JOBBKLUBB,
    ENKELTPLASS_AMO,
    ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG,
    FORSØK_OPPLÆRING_LENGRE_VARIGHET,
    GRUPPE_AMO,
    GRUPPE_VGS_OG_HØYERE_YRKESFAG,
    HØYERE_UTDANNING,
    INDIVIDUELL_JOBBSTØTTE,
    INDIVIDUELL_KARRIERESTØTTE_UNG,
    JOBBKLUBB,
    OPPFØLGING,
    UTVIDET_OPPFØLGING_I_NAV,
    UTVIDET_OPPFØLGING_I_OPPLÆRING;

    override fun tilFagsystem(): Fagsystem = Fagsystem.TILTAKSPENGER
}

enum class StønadTypeTilleggsstønader : StønadType {
    TILSYN_BARN_ENSLIG_FORSØRGER,
    TILSYN_BARN_AAP,
    TILSYN_BARN_ETTERLATTE,
    LÆREMIDLER_ENSLIG_FORSØRGER,
    LÆREMIDLER_AAP,
    LÆREMIDLER_ETTERLATTE,
    BOUTGIFTER_AAP,
    BOUTGIFTER_ENSLIG_FORSØRGER,
    BOUTGIFTER_ETTERLATTE;

    override fun tilFagsystem(): Fagsystem = Fagsystem.TILLEGGSSTØNADER
}

enum class StønadTypeAAP: StønadType {
    AAP_UNDER_ARBEIDSAVKLARING;

    override fun tilFagsystem(): Fagsystem = Fagsystem.AAP
}

val objectMapper: ObjectMapper = ObjectMapper()
    .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
    .setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE)
    .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
    .setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY)
    .registerKotlinModule()
    .registerModule(
        JavaTimeModule()
            .addDeserializer(
                YearMonth::class.java,
                YearMonthDeserializer(DateTimeFormatter.ofPattern("u-MM")) // Denne trengs for å parse år over 9999 riktig.
            )
    )
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
