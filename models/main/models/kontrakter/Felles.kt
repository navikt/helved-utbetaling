@file:UseSerializers(models.kotlinx.LocalDateSerializer::class)

package models.kontrakter

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.UseSerializers
import java.lang.IllegalArgumentException
import java.time.LocalDate

@Serializable
data class BrukersNavKontor(
    val enhet: String,
    val gjelderFom: LocalDate? = null,
)

@Serializable
enum class Fagsystem(val kode: String) {
    AAP("AAP"),
    DAGPENGER("DP"),
    TILTAKSPENGER("TILTPENG"),
    TILLEGGSSTØNADER("TILLST");
}

fun String.tilFagsystem(): Fagsystem = Fagsystem.entries.find { it.kode == this } ?: throw IllegalArgumentException("$this er ukjent fagsystem")

object GyldigSakId {
    const val MAKSLENGDE = 25
    const val BESKRIVELSE = "På grunn av tekniske begrensninger hos OS/UR er det en lengdebegrensning på $MAKSLENGDE tegn for sakId"
}

object GyldigBehandlingId {
    const val MAKSLENGDE = 30
    const val BESKRIVELSE = "På grunn av tekniske begrensninger hos OS/UR er det en lengdebegrensning på $MAKSLENGDE tegn for behandlingId"
}

object IdentSerializer : KSerializer<Ident> {
    override val descriptor = PrimitiveSerialDescriptor("Ident", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Ident) = encoder.encodeString(value.verdi)
    override fun deserialize(decoder: Decoder): Ident {
        val jsonDecoder = decoder as? JsonDecoder
        val verdi = if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            when (element) {
                is JsonPrimitive -> element.content
                is JsonObject -> element["verdi"]!!.jsonPrimitive.content
                else -> error("Unexpected JSON for Personident: $element")
            }
        } else {
            decoder.decodeString()
        }
        return when (verdi.length) {
            9 -> Organisasjonsnummer(verdi)
            11 -> Personident(verdi)
            else -> throw Exception("Ugyldig ident")
        }
    }
}

@Serializable(with = IdentSerializer::class)
sealed class Ident(val verdi: String)

class Organisasjonsnummer(verdi: String) : Ident(verdi) {
    init {
        check(gyldig()) { "Organisasjonsnummeret er ugyldig" }
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

    companion object {}

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

@Serializable
enum class Satstype {
    DAGLIG,
    DAGLIG_INKL_HELG,
    MÅNEDLIG,
    ENGANGS,
}

object StønadTypeSerializer: KSerializer<StønadType> {
    override val descriptor = PrimitiveSerialDescriptor("StønadType", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: StønadType) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): StønadType {
        val jsonDecoder = decoder as? JsonDecoder
        val verdi = if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            when (element) {
                is JsonPrimitive -> element.content
                is JsonObject -> element["name"]!!.jsonPrimitive.content
                else -> error("Unexpected JSON for StønadType: $element")
            }
        } else {
            decoder.decodeString()
        }
        val stønadstypeDagpenger = StønadTypeDagpenger.entries.find { it.name == verdi }
        val stønadstypeTiltakspenger = StønadTypeTiltakspenger.entries.find { it.name == verdi }
        val stønadstypeTilleggsstønader = StønadTypeTilleggsstønader.entries.find { it.name == verdi }
        return stønadstypeDagpenger ?: stønadstypeTiltakspenger ?: stønadstypeTilleggsstønader ?: error("Ukjent stønadstype: $verdi")
    }
}

@Serializable(with = StønadTypeSerializer::class)
sealed interface StønadType {
    val name: String
    fun tilFagsystem(): Fagsystem
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
    UTVIDET_OPPFØLGING_I_OPPLÆRING,

    ARBEIDSMARKEDSOPPLÆRING_AMO,
    NORSKOPPLÆRING_GRUNNLEGGENDE_FERDIGHETER,
    FAG_OG_YRKESOPPLÆRING,
    STUDIESPESIALISERING,
    FAGSKOLE;

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

