package utsjekk.simulering

import no.nav.utsjekk.kontrakter.felles.Personident
import no.nav.utsjekk.kontrakter.iverksett.ForrigeIverksettingV2Dto
import no.nav.utsjekk.kontrakter.iverksett.UtbetalingV2Dto
import java.time.LocalDate

data class SimuleringRequestV2Dto(
    val sakId: String,
    val behandlingId: String,
    val personident: Personident,
    val saksbehandlerId: String,
    val utbetalinger: List<UtbetalingV2Dto>,
    val forrigeIverksetting: ForrigeIverksettingV2Dto? = null,
)

data class SimuleringResponsDto(
    val oppsummeringer: List<OppsummeringForPeriode>,
    val detaljer: SimuleringDetaljer,
)

data class OppsummeringForPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
    val tidligereUtbetalt: Int,
    val nyUtbetaling: Int,
    val totalEtterbetaling: Int,
    val totalFeilutbetaling: Int,
)
