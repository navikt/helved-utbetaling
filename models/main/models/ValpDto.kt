@file:UseSerializers(models.kotlinx.LocalDateSerializer::class, models.kotlinx.UUIDSerializer::class, models.kotlinx.InstantSerializer::class)

package models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import models.ValpUtbetaling.Tiltakskode
import models.ValpUtbetaling.Tilskuddstype
import java.time.*
import java.util.UUID

@Serializable
data class ValpUtbetaling(
    val id: UUID,
    val sakId: String,
    val behandlingId: String,
    val personIdent: String,
    val periode: Periode,
    val belop: UInt,
    val tilskuddstype: Tilskuddstype,
    val tiltakskode: Tiltakskode,
    val saksbehandler: String,
    val beslutter: String? = null,
    val besluttetTidspunkt: Instant,
    val dryrun: Boolean,
) {
    @Serializable
    data class Periode(
        val fom: LocalDate,
        val tom: LocalDate,
    )

    enum class Tiltakskode {
        ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING,
        ENKELTPLASS_FAG_OG_YRKESOPPLAERING,
        ARBEIDSMARKEDSOPPLAERING,
        NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
        STUDIESPESIALISERING,
        FAG_OG_YRKESOPPLAERING,
        HOYERE_YRKESFAGLIG_UTDANNING,
        HOYERE_UTDANNING,
    }

    enum class Tilskuddstype {
        SKOLEPENGER,
        STUDIEREISE,
        EKSAMENSGEBYR,
        SEMESTERAVGIFT,
        INTEGRERT_BOTILBUD,
    }

    companion object {
        fun toDomain(
            originalKey: String,
            dto: ValpUtbetaling,
            uidsPåSak: Set<UtbetalingId>?,
        ): Utbetaling {
            val stønad = stønad(dto.tiltakskode, dto.tilskuddstype)
            return when (dto.belop) {
                0u -> delete(originalKey, dto, stønad)
                else -> create(originalKey, dto, uidsPåSak, stønad)
            }
        }

        private fun create(
            originalKey: String,
            dto: ValpUtbetaling,
            uidsPåSak: Set<UtbetalingId>?,
            stønad: StønadTypeValp,
        ) = Utbetaling(
            dryrun = dto.dryrun,
            originalKey = originalKey,
            fagsystem = Fagsystem.VALP,
            uid = UtbetalingId(dto.id),
            action = Action.CREATE,
            førsteUtbetalingPåSak = uidsPåSak == null,
            sakId = SakId(dto.sakId),
            behandlingId = BehandlingId(dto.behandlingId),
            lastPeriodeId = PeriodeId(),
            personident = Personident(dto.personIdent),
            vedtakstidspunkt = LocalDateTime.ofInstant(dto.besluttetTidspunkt, ZoneId.systemDefault()),
            stønad = stønad,
            beslutterId = dto.beslutter?.let(::Navident) ?: Navident("teamvalp"),
            saksbehandlerId = Navident(dto.saksbehandler),
            periodetype = Periodetype.EN_GANG,
            avvent = null,
            perioder = listOf(Utbetalingsperiode(dto.periode.fom, dto.periode.tom, dto.belop))
        )

        private fun delete(
            originalKey: String,
            dto: ValpUtbetaling,
            stønad: StønadTypeValp
        ) = Utbetaling(
            dryrun = dto.dryrun,
            originalKey = originalKey,
            fagsystem = Fagsystem.VALP,
            uid = UtbetalingId(dto.id),
            action = Action.FAKE_DELETE,
            førsteUtbetalingPåSak = false,
            sakId = SakId(dto.sakId),
            behandlingId = BehandlingId(dto.behandlingId),
            lastPeriodeId = PeriodeId(),
            personident = Personident(dto.personIdent),
            vedtakstidspunkt = LocalDateTime.ofInstant(dto.besluttetTidspunkt, ZoneId.systemDefault()),
            stønad = stønad,
            beslutterId = dto.beslutter?.let(::Navident) ?: Navident("teamvalp"),
            saksbehandlerId = Navident(dto.saksbehandler),
            periodetype = Periodetype.EN_GANG,
            avvent = null,
            perioder = listOf(Utbetalingsperiode(dto.periode.fom, dto.periode.tom, 1u)),
        )
    }
}

private fun stønad(tiltak: Tiltakskode, tilskudd: Tilskuddstype) =
    when (tiltak) {
        Tiltakskode.HOYERE_UTDANNING -> when (tilskudd) {
            Tilskuddstype.SKOLEPENGER -> StønadTypeValp.HOYERE_UTDANNING_SKOLEPENGER
            Tilskuddstype.STUDIEREISE -> StønadTypeValp.HOYERE_UTDANNING_STUDIEREISE
            Tilskuddstype.EKSAMENSGEBYR -> StønadTypeValp.HOYERE_UTDANNING_EKSAMENSGEBYR
            Tilskuddstype.SEMESTERAVGIFT -> StønadTypeValp.HOYERE_UTDANNING_SEMESTERAVGIFT
            Tilskuddstype.INTEGRERT_BOTILBUD -> StønadTypeValp.HOYERE_UTDANNING_INTEGRERT_BOTILBUD
        }

        Tiltakskode.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING -> when (tilskudd) {
            Tilskuddstype.SKOLEPENGER -> StønadTypeValp.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING_SKOLEPENGER
            Tilskuddstype.STUDIEREISE -> StønadTypeValp.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING_STUDIEREISE
            Tilskuddstype.EKSAMENSGEBYR -> StønadTypeValp.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING_EKSAMENSGEBYR
            Tilskuddstype.SEMESTERAVGIFT -> StønadTypeValp.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING_SEMESTERAVGIFT
            Tilskuddstype.INTEGRERT_BOTILBUD -> StønadTypeValp.ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING_INTEGRERT_BOTILBUD
        }

        Tiltakskode.ENKELTPLASS_FAG_OG_YRKESOPPLAERING -> when (tilskudd) {
            Tilskuddstype.SKOLEPENGER -> StønadTypeValp.ENKELTPLASS_FAG_OG_YRKESOPPLAERING_SKOLEPENGER
            Tilskuddstype.STUDIEREISE -> StønadTypeValp.ENKELTPLASS_FAG_OG_YRKESOPPLAERING_STUDIEREISE
            Tilskuddstype.EKSAMENSGEBYR -> StønadTypeValp.ENKELTPLASS_FAG_OG_YRKESOPPLAERING_EKSAMENSGEBYR
            Tilskuddstype.SEMESTERAVGIFT -> StønadTypeValp.ENKELTPLASS_FAG_OG_YRKESOPPLAERING_SEMESTERAVGIFT
            Tilskuddstype.INTEGRERT_BOTILBUD -> StønadTypeValp.ENKELTPLASS_FAG_OG_YRKESOPPLAERING_INTEGRERT_BOTILBUD
        }

        Tiltakskode.ARBEIDSMARKEDSOPPLAERING -> when (tilskudd) {
            Tilskuddstype.SKOLEPENGER -> StønadTypeValp.ARBEIDSMARKEDSOPPLAERING_SKOLEPENGER
            Tilskuddstype.STUDIEREISE -> StønadTypeValp.ARBEIDSMARKEDSOPPLAERING_STUDIEREISE
            Tilskuddstype.EKSAMENSGEBYR -> StønadTypeValp.ARBEIDSMARKEDSOPPLAERING_EKSAMENSGEBYR
            Tilskuddstype.SEMESTERAVGIFT -> StønadTypeValp.ARBEIDSMARKEDSOPPLAERING_SEMESTERAVGIFT
            Tilskuddstype.INTEGRERT_BOTILBUD -> StønadTypeValp.ARBEIDSMARKEDSOPPLAERING_INTEGRERT_BOTILBUD
        }

        Tiltakskode.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV -> when (tilskudd) {
            Tilskuddstype.SKOLEPENGER -> StønadTypeValp.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV_SKOLEPENGER
            Tilskuddstype.STUDIEREISE -> StønadTypeValp.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV_STUDIEREISE
            Tilskuddstype.EKSAMENSGEBYR -> StønadTypeValp.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV_EKSAMENSGEBYR
            Tilskuddstype.SEMESTERAVGIFT -> StønadTypeValp.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV_SEMESTERAVGIFT
            Tilskuddstype.INTEGRERT_BOTILBUD -> StønadTypeValp.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV_INTEGRERT_BOTILBUD
        }

        Tiltakskode.STUDIESPESIALISERING -> when (tilskudd) {
            Tilskuddstype.SKOLEPENGER -> StønadTypeValp.STUDIESPESIALISERING_SKOLEPENGER
            Tilskuddstype.STUDIEREISE -> StønadTypeValp.STUDIESPESIALISERING_STUDIEREISE
            Tilskuddstype.EKSAMENSGEBYR -> StønadTypeValp.STUDIESPESIALISERING_EKSAMENSGEBYR
            Tilskuddstype.SEMESTERAVGIFT -> StønadTypeValp.STUDIESPESIALISERING_SEMESTERAVGIFT
            Tilskuddstype.INTEGRERT_BOTILBUD -> StønadTypeValp.STUDIESPESIALISERING_INTEGRERT_BOTILBUD
        }

        Tiltakskode.FAG_OG_YRKESOPPLAERING -> when (tilskudd) {
            Tilskuddstype.SKOLEPENGER -> StønadTypeValp.FAG_OG_YRKESOPPLAERING_SKOLEPENGER
            Tilskuddstype.STUDIEREISE -> StønadTypeValp.FAG_OG_YRKESOPPLAERING_STUDIEREISE
            Tilskuddstype.EKSAMENSGEBYR -> StønadTypeValp.FAG_OG_YRKESOPPLAERING_EKSAMENSGEBYR
            Tilskuddstype.SEMESTERAVGIFT -> StønadTypeValp.FAG_OG_YRKESOPPLAERING_SEMESTERAVGIFT
            Tilskuddstype.INTEGRERT_BOTILBUD -> StønadTypeValp.FAG_OG_YRKESOPPLAERING_INTEGRERT_BOTILBUD
        }

        Tiltakskode.HOYERE_YRKESFAGLIG_UTDANNING -> when (tilskudd) {
            Tilskuddstype.SKOLEPENGER -> StønadTypeValp.HOYERE_YRKESFAGLIG_UTDANNING_SKOLEPENGER
            Tilskuddstype.STUDIEREISE -> StønadTypeValp.HOYERE_YRKESFAGLIG_UTDANNING_STUDIEREISE
            Tilskuddstype.EKSAMENSGEBYR -> StønadTypeValp.HOYERE_YRKESFAGLIG_UTDANNING_EKSAMENSGEBYR
            Tilskuddstype.SEMESTERAVGIFT -> StønadTypeValp.HOYERE_YRKESFAGLIG_UTDANNING_SEMESTERAVGIFT
            Tilskuddstype.INTEGRERT_BOTILBUD -> StønadTypeValp.HOYERE_YRKESFAGLIG_UTDANNING_INTEGRERT_BOTILBUD
        }
    }
