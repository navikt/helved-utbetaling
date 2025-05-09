package models.kontrakter.iverksett

import models.kontrakter.felles.*
import java.time.LocalDate
import java.time.LocalDateTime
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.JsonNode

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
)

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
