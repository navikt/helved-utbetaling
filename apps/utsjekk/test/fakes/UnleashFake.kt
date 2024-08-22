package fakes

import no.nav.utsjekk.kontrakter.felles.Fagsystem
import utsjekk.UnleashConfig
import utsjekk.featuretoggle.FeatureToggles
import java.net.URI


class UnleashFake : FeatureToggles {
    companion object {
        private val disabled = mutableListOf<Fagsystem>()

        val config = UnleashConfig(
            host = "http://localhost".let(::URI),
            apiKey = "secret",
            appName = "utsjekk",
            cluster = "test",
        )
    }

    fun reset() = disabled.clear()
    fun disable(fagsystem: Fagsystem) = disabled.add(fagsystem)

    override fun isDisabled(fagsystem: Fagsystem) = disabled.contains(fagsystem)
}