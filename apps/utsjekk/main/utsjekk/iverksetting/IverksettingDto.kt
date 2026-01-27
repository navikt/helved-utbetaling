package utsjekk.iverksetting

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.JsonNode
import models.DocumentedErrors
import models.badRequest
import models.kontrakter.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

enum class IverksettStatus {
    SENDT_TIL_OPPDRAG,
    FEILET_MOT_OPPDRAG,
    OK,
    IKKE_PÅBEGYNT,
    OK_UTEN_UTBETALING,
}

data class IverksettV2Dto(
    val sakId: String,
    val behandlingId: String,
    val iverksettingId: String? = null,
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

data class VedtaksdetaljerV2Dto(
    val vedtakstidspunkt: LocalDateTime,
    val saksbehandlerId: String,
    val beslutterId: String,
    val utbetalinger: List<UtbetalingV2Dto> = emptyList(),
)

data class UtbetalingV2Dto(
    val beløp: UInt,
    val satstype: Satstype,
    val fraOgMedDato: LocalDate,
    val tilOgMedDato: LocalDate,
    val stønadsdata: StønadsdataDto,
)

data class ForrigeIverksettingV2Dto(
    val behandlingId: String,
    val iverksettingId: String? = null,
)

data class StatusEndretMelding(
    val sakId: String,
    val behandlingId: String,
    val iverksettingId: String?,
    val fagsystem: Fagsystem,
    val status: IverksettStatus,
)

enum class Ferietillegg {
    ORDINÆR,
    AVDØD,
}

sealed class StønadsdataDto(open val stønadstype: StønadType) {
    companion object {
        @JsonCreator
        @JvmStatic
        fun deserialize(json: JsonNode) =
            listOf(
                StønadsdataAAPDto::deserialiser,
                StønadsdataDagpengerDto::deserialiser,
                StønadsdataTiltakspengerV2Dto::deserialiser,
                StønadsdataTilleggsstønaderDto::deserialiser
            )
                .map { it(json) }
                .firstOrNull { it != null } ?: error("Klarte ikke deserialisere stønadsdata")
    }
}

data class StønadsdataDagpengerDto(
    override val stønadstype: StønadTypeDagpenger,
    val ferietillegg: Ferietillegg? = null,
    val meldekortId: String,
    val fastsattDagsats: UInt,
) :
    StønadsdataDto(stønadstype) {
    companion object {
        fun deserialiser(json: JsonNode) = try {
            StønadsdataDagpengerDto(
                stønadstype = StønadTypeDagpenger.valueOf(json["stønadstype"].asText()),
                meldekortId = json["meldekortId"].asText(),
                fastsattDagsats = json["fastsattDagsats"].asInt().toUInt(),
                ferietillegg = json["ferietillegg"]?.asText()
                    .takeIf { it != null && it != "null" }
                    ?.let { Ferietillegg.valueOf(it) }
            )
        } catch (_: Exception) {
            null
        }
    }
}

data class StønadsdataTiltakspengerV2Dto(
    override val stønadstype: StønadTypeTiltakspenger,
    val barnetillegg: Boolean = false,
    val brukersNavKontor: String,
    val meldekortId: String,
) : StønadsdataDto(stønadstype) {
    companion object {
        fun deserialiser(json: JsonNode) = try {
            StønadsdataTiltakspengerV2Dto(
                stønadstype = StønadTypeTiltakspenger.valueOf(json["stønadstype"].asText()),
                barnetillegg = json["barnetillegg"]?.asBoolean() ?: false,
                brukersNavKontor = json["brukersNavKontor"].asText(),
                meldekortId = json["meldekortId"].asText(),
            )
        } catch (_: Exception) {
            null
        }
    }
}

data class StønadsdataTilleggsstønaderDto(
    override val stønadstype: StønadTypeTilleggsstønader,
    val brukersNavKontor: String? = null,
) : StønadsdataDto(stønadstype) {
    companion object {
        fun deserialiser(json: JsonNode) = try {
            StønadsdataTilleggsstønaderDto(
                stønadstype = StønadTypeTilleggsstønader.valueOf(json["stønadstype"].asText()),
                brukersNavKontor = json["brukersNavKontor"]?.asText().takeIf { it != null && it != "null" },
            )
        } catch (_: Exception) {
            null
        }
    }
}

data class StønadsdataAAPDto(
    override val stønadstype: StønadTypeAAP,
    val fastsattDagsats: UInt? = null,
) : StønadsdataDto (stønadstype) {
    companion object {
        fun deserialiser(json: JsonNode) = try {
            StønadsdataAAPDto(
                stønadstype = StønadTypeAAP.valueOf(json["stønadstype"].asText()),
                fastsattDagsats = json["fastsattDagsats"]?.asText().takeIf { it != null && it != "null" }?.toUInt(),
            )
        } catch (_: Exception) {
            null
        }
    }
}

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
