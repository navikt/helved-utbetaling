package models.kontrakter.oppdrag

import models.kontrakter.felles.*
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class GrensesnittavstemmingRequest(
    val fagsystem: Fagsystem,
    val fra: LocalDateTime,
    val til: LocalDateTime
)

data class OppdragIdDto(
    val fagsystem: Fagsystem,
    val sakId: String,
    val behandlingId: String,
    val iverksettingId: String?,
) {
    override fun toString(): String =
        "OppdragId(fagsystem=$fagsystem, sakId=$sakId, behandlingsId=$behandlingId, iverksettingId=$iverksettingId)"
}

data class OppdragStatusDto(
    val status: OppdragStatus,
    val feilmelding: String?,
)

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
