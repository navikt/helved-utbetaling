package utsjekk.simulering

import no.nav.utsjekk.kontrakter.felles.Fagsystem
import utsjekk.iverksetting.*
import java.time.LocalDate

fun Simulering.Mapper.from(dto: SimuleringRequestV2Dto, fagsystem: Fagsystem) = Simulering(
    behandlingsinformasjon = Behandlingsinformasjon(
        saksbehandlerId = dto.saksbehandlerId,
        beslutterId = dto.saksbehandlerId,
        fagsakId = SakId(dto.sakId),
        fagsystem = fagsystem,
        behandlingId = BehandlingId(dto.behandlingId),
        personident = dto.personident.verdi,
        vedtaksdato = LocalDate.now(),
        brukersNavKontor = null,
        iverksettingId = null,
    ),
    nyTilkjentYtelse = dto.utbetalinger.toTilkjentYtelse(),
    forrigeIverksetting = dto.forrigeIverksetting?.let {
        ForrigeIverksetting(
            behandlingId = BehandlingId(it.behandlingId),
            iverksettingId = it.iverksettingId?.let(::IverksettingId),
        )
    },
)
