@file:UseSerializers(models.kotlinx.LocalDateSerializer::class, models.kotlinx.LocalDateTimeSerializer::class, models.kotlinx.UUIDSerializer::class)
package utsjekk.iverksetting

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import models.DocumentedErrors
import models.badRequest
import models.kontrakter.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@Serializable
enum class IverksettStatus {
    SENDT_TIL_OPPDRAG,
    FEILET_MOT_OPPDRAG,
    OK,
    IKKE_PÅBEGYNT,
    OK_UTEN_UTBETALING,
}

@Serializable
data class IverksettV2Dto(
    val sakId: String,
    val behandlingId: String,
    val iverksettingId: String? = null,
    @Serializable(with = PersonidentSerializer::class)
    val personident: Personident,
    val vedtak: VedtaksdetaljerV2Dto =
        VedtaksdetaljerV2Dto(
            vedtakstidspunkt = LocalDateTime.now(),
            saksbehandlerId = "",
            beslutterId = "",
        ),
    val forrigeIverksetting: ForrigeIverksettingV2Dto? = null,
) {
    fun validate() {
        sakIdTilfredsstillerLengdebegrensning(this)
        behandlingIdTilfredsstillerLengdebegrensning(this)
        fraOgMedKommerFørTilOgMedIUtbetalingsperioder(this)
        utbetalingsperioderMedLikStønadsdataOverlapperIkkeITid(this)
        utbetalingsperioderSamsvarerMedSatstype(this)
        iverksettingIdSkalEntenIkkeVæreSattEllerVæreSattForNåværendeOgForrige(this)
        ingenUtbetalingsperioderHarStønadstypeEØSOgFerietilleggTilAvdød(this)
    }
}

@Serializable
data class VedtaksdetaljerV2Dto(
    val vedtakstidspunkt: LocalDateTime,
    val saksbehandlerId: String,
    val beslutterId: String,
    val utbetalinger: List<UtbetalingV2Dto> = emptyList(),
)

@Serializable
data class UtbetalingV2Dto(
    val beløp: UInt,
    val satstype: Satstype,
    val fraOgMedDato: LocalDate,
    val tilOgMedDato: LocalDate,
    val stønadsdata: StønadsdataDto,
)

@Serializable
data class ForrigeIverksettingV2Dto(
    val behandlingId: String,
    val iverksettingId: String? = null,
)

@Serializable
data class StatusEndretMelding(
    val sakId: String,
    val behandlingId: String,
    val iverksettingId: String?,
    val fagsystem: Fagsystem,
    val status: IverksettStatus,
)

@Serializable
enum class Ferietillegg {
    ORDINÆR,
    AVDØD,
}

@Serializable(with = StønadsdataDtoSerializer::class)
sealed class StønadsdataDto {
    abstract val stønadstype: StønadType
}

object StønadsdataDtoSerializer: JsonContentPolymorphicSerializer<StønadsdataDto>(StønadsdataDto::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<StønadsdataDto> {
        val stønadstype = element.jsonObject["stønadstype"]?.jsonPrimitive?.contentOrNull ?: error("Mangler stønadstype")
        return when {
            StønadTypeDagpenger.entries.any { it.name == stønadstype } -> StønadsdataDagpengerDto.serializer()
            StønadTypeTiltakspenger.entries.any { it.name == stønadstype } -> StønadsdataTiltakspengerV2Dto.serializer()
            StønadTypeTilleggsstønader.entries.any { it.name == stønadstype } -> StønadsdataTilleggsstønaderDto.serializer()
            StønadTypeAAP.entries.any { it.name == stønadstype } -> StønadsdataAAPDto.serializer()
            else -> error("Ukjent stønadstype: $stønadstype")
        }
    }
}

@Serializable
data class StønadsdataDagpengerDto(
    override val stønadstype: StønadTypeDagpenger,
    val ferietillegg: Ferietillegg? = null,
    val meldekortId: String,
    val fastsattDagsats: UInt,
) : StønadsdataDto()


@Serializable
data class StønadsdataTiltakspengerV2Dto(
    override val stønadstype: StønadTypeTiltakspenger,
    val barnetillegg: Boolean = false,
    val brukersNavKontor: String,
    val meldekortId: String,
) : StønadsdataDto()

@Serializable
data class StønadsdataTilleggsstønaderDto(
    override val stønadstype: StønadTypeTilleggsstønader,
    val brukersNavKontor: String? = null,
) : StønadsdataDto()

@Serializable
data class StønadsdataAAPDto(
    override val stønadstype: StønadTypeAAP,
    val fastsattDagsats: UInt? = null,
) : StønadsdataDto ()

fun sakIdTilfredsstillerLengdebegrensning(iverksettDto: IverksettV2Dto) {
    if (iverksettDto.sakId.length !in 1..GyldigSakId.MAKSLENGDE) {
        badRequest(DocumentedErrors.Async.Utbetaling.UGYLDIG_SAK_ID)
    }
}

fun behandlingIdTilfredsstillerLengdebegrensning(iverksettDto: IverksettV2Dto) {
    if (iverksettDto.behandlingId.length !in 1..GyldigBehandlingId.MAKSLENGDE) {
        badRequest(DocumentedErrors.Async.Utbetaling.UGYLDIG_BEHANDLING_ID)
    }
}

fun fraOgMedKommerFørTilOgMedIUtbetalingsperioder(iverksettDto: IverksettV2Dto) {
    val alleErOk =
        iverksettDto.vedtak.utbetalinger.all {
            !it.tilOgMedDato.isBefore(it.fraOgMedDato)
        }

    if (!alleErOk) {
        badRequest(DocumentedErrors.Async.Utbetaling.UGYLDIG_PERIODE)
    }
}

fun utbetalingsperioderMedLikStønadsdataOverlapperIkkeITid(iverksettDto: IverksettV2Dto) {
    val allePerioderErUavhengige =
        iverksettDto.vedtak.utbetalinger
            .groupBy { it.stønadsdata }
            .all { utbetalinger ->
                utbetalinger.value
                    .sortedBy { it.fraOgMedDato }
                    .windowed(2, 1, false) {
                        val førstePeriodeTom = it.first().tilOgMedDato
                        val sistePeriodeFom = it.last().fraOgMedDato

                        førstePeriodeTom.isBefore(sistePeriodeFom)
                    }.all { it }
            }

    if (!allePerioderErUavhengige) {
        badRequest("Utbetalinger inneholder perioder som overlapper i tid")
    }
}

fun utbetalingsperioderSamsvarerMedSatstype(iverksettDto: IverksettV2Dto) {
    val satstype =
        iverksettDto.vedtak.utbetalinger
            .firstOrNull()
            ?.satstype

    if (satstype == Satstype.MÅNEDLIG) {
        val alleFomErStartenAvMåned = iverksettDto.vedtak.utbetalinger.all { it.fraOgMedDato.dayOfMonth == 1 }
        val alleTomErSluttenAvMåned =
            iverksettDto.vedtak.utbetalinger.all {
                val sisteDag = YearMonth.from(it.tilOgMedDato).atEndOfMonth().dayOfMonth
                it.tilOgMedDato.dayOfMonth == sisteDag
            }

        if (!(alleTomErSluttenAvMåned && alleFomErStartenAvMåned)) {
            badRequest("Det finnes utbetalinger med månedssats der periodene ikke samsvarer med hele måneder")
        }
    }
}

fun iverksettingIdSkalEntenIkkeVæreSattEllerVæreSattForNåværendeOgForrige(iverksettDto: IverksettV2Dto) {
    if (iverksettDto.iverksettingId != null &&
        iverksettDto.forrigeIverksetting != null &&
        iverksettDto.forrigeIverksetting.iverksettingId == null
    ) {
        badRequest("IverksettingId er satt for nåværende iverksetting, men ikke forrige iverksetting")
    }

    if (iverksettDto.iverksettingId == null &&
        iverksettDto.forrigeIverksetting != null &&
        iverksettDto.forrigeIverksetting.iverksettingId != null
    ) {
        badRequest("IverksettingId er satt for forrige iverksetting, men ikke nåværende iverksetting")
    }
}

fun ingenUtbetalingsperioderHarStønadstypeEØSOgFerietilleggTilAvdød(iverksettDto: IverksettV2Dto) {
    val ugyldigKombinasjon =
        iverksettDto.vedtak.utbetalinger.any {
            if (it.stønadsdata is StønadsdataDagpengerDto) {
                val sd = it.stønadsdata// as StønadsdataDagpengerDto
                sd.stønadstype == StønadTypeDagpenger.DAGPENGER_EØS && sd.ferietillegg == Ferietillegg.AVDØD
            } else {
                false
            }
        }

    if (ugyldigKombinasjon) {
        badRequest("Ferietillegg til avdød er ikke tillatt for stønadstypen ${StønadTypeDagpenger.DAGPENGER_EØS}")
    }
}

object PersonidentSerializer : KSerializer<Personident> {
    override val descriptor = PrimitiveSerialDescriptor("Personident", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Personident) = encoder.encodeString(value.verdi)
    override fun deserialize(decoder: Decoder): Personident {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            val verdi = when (element) {
                is JsonPrimitive -> element.content
                is JsonObject -> element["verdi"]!!.jsonPrimitive.content
                else -> error("Unexpected JSON for Personident: $element")
            }
            return Personident(verdi)
        }
        return Personident(decoder.decodeString())
    }
}
