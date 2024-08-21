package utsjekk.iverksetting

import no.nav.utsjekk.kontrakter.iverksett.IverksettStatus
import utsjekk.ApiError.Companion.serviceUnavailable
import utsjekk.featuretoggle.FeatureToggles

class IverksettingService(private val toggles: FeatureToggles) {
    fun iverksett(iverksetting: Iverksetting) {
        val fagsystem = iverksetting.fagsak.fagsystem
        if (toggles.isDisabled(fagsystem)) {
            serviceUnavailable("Iverksetting er skrudd av for fagsystem $fagsystem")
        }
    }

    fun utledStatus(
        client: Client,
        sakId: SakId,
        behandlingId: BehandlingId,
        iverksettingId: IverksettingId? = null
    ): IverksettStatus {
        TODO("impl")
    }
}

@JvmInline
value class Client(private val name: String)

@JvmInline
value class SakId(private val id: String)

@JvmInline
value class BehandlingId(private val id: String)

@JvmInline
value class IverksettingId(private val id: String)