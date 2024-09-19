package utsjekk

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.UnleashContext
import io.getunleash.strategy.Strategy
import io.getunleash.util.UnleashConfig
import libs.utils.appLog
import no.nav.utsjekk.kontrakter.felles.Fagsystem

interface FeatureToggles {
    fun isDisabled(fagsystem: Fagsystem): Boolean
}

class UnleashFeatureToggles(config: utsjekk.UnleashConfig) : FeatureToggles {
    private val unleash: Unleash = DefaultUnleash(
        UnleashConfig.builder()
            .appName(config.appName)
            .unleashAPI("${config.host}/api")
            .apiKey(config.apiKey)
            .disablePolling()
            .unleashContextProvider {
                UnleashContext.builder()
                    .environment(config.cluster)
                    .appName(config.appName)
                    .build()
            }
            .build(),
        ByEnvironmentStrategy
    )

    private val killswitches: Map<Fagsystem, String> = mapOf(
        Fagsystem.TILLEGGSSTØNADER to "utsjekk.stopp-iverksetting-tilleggsstonader",
        Fagsystem.DAGPENGER to "utsjekk.stopp-iverksetting-dagpenger",
        Fagsystem.TILTAKSPENGER to "utsjekk.stopp-iverksetting-tiltakspenger",
    )

    override fun isDisabled(fagsystem: Fagsystem): Boolean {
        return when (val killswitch = killswitches[fagsystem]) {
            null -> true.also {
                appLog.error("Feature toggling av fagsystem $fagsystem ikke implementert.")
            }

            else -> unleash.isEnabled(killswitch)
        }
    }
}

private object ByEnvironmentStrategy : Strategy {
    override fun getName(): String = "byEnvironment"
    override fun isEnabled(map: Map<String, String>): Boolean {
        val ctx = UnleashContext.builder().build()
        return ctx.environment.map { map["miljø"]?.split(',')?.contains(it) ?: false }.orElse(false)
    }
}