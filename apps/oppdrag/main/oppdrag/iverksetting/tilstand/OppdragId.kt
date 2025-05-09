package oppdrag.iverksetting.tilstand

import models.kontrakter.felles.Fagsystem
import models.kontrakter.felles.tilFagsystem
import no.trygdeetaten.skjema.oppdrag.Oppdrag

data class OppdragId(
    val fagsystem: Fagsystem,
    val fagsakId: String,
    val behandlingId: String,
    val iverksettingId: String?,
)

internal val Oppdrag.id: OppdragId
    get() =
        OppdragId(
            fagsystem = oppdrag110.kodeFagomraade.tilFagsystem(),
            fagsakId = oppdrag110.fagsystemId,
            behandlingId = oppdrag110.oppdragsLinje150s?.get(0)?.henvisning!!,
            iverksettingId = null,
        )

val OppdragLager.id: OppdragId
    get() =
        OppdragId(
            fagsystem = this.fagsystem.tilFagsystem(),
            fagsakId = this.fagsak_id,
            behandlingId = this.behandling_id,
            iverksettingId = this.iverksetting_id,
        )
