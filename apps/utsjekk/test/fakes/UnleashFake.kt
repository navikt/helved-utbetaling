package fakes

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.utsjekk.kontrakter.felles.Fagsystem
import org.intellij.lang.annotations.Language
import port
import utsjekk.UnleashConfig
import java.net.URI
import java.util.concurrent.atomic.AtomicLong


class UnleashFake : AutoCloseable {
    private val unleash = embeddedServer(Netty, port = 0, module = Application::unleash).apply { start() }

    val config by lazy {
        UnleashConfig(
            host = "http://localhost:${unleash.port()}".let(::URI),
            apiKey = "secret",
            appName = "utsjekk",
            cluster = "test",
        )
    }

    override fun close() = unleash.stop(0, 0)
    fun reset() = disabled.clear()
    fun disable(fagsystem: Fagsystem) = disabled.add(fagsystem)
}

var counter = AtomicLong(0) // sjekker hvor mange ganger ScheduledFeatureExecutor rekker å fetche features fra unleach

private fun Application.unleash() {
    routing {
        get("/api/client/features") {
            counter.incrementAndGet()
            call.respondText(features)
        }
    }
}

private val disabled = mutableListOf<Fagsystem>()

private val features
    @Language("JSON")
    get() = """
    {
      "version": 2,
      "features": [
        {
          "name": "utsjekk.stopp-iverksetting-tiltakspenger",
          "type": "kill-switch",
          "enabled": ${disabled.contains(Fagsystem.TILTAKSPENGER)},
          "project": "default",
          "stale": true,
          "strategies": [
            {
              "name": "flexibleRollout",
              "constraints": [],
              "parameters": {
                "groupId": "utsjekk.stopp-iverksetting-tiltakspenger",
                "rollout": "100",
                "stickiness": "default"
              },
              "variants": []
            }
          ],
          "variants": [],
          "description": "Når denne er skrudd på stopper den iverksetting av utbetalinger for tiltakspenger",
          "impressionData": false
        },
        {
          "name": "utsjekk.stopp-iverksetting-dagpenger",
          "type": "kill-switch",
          "enabled": ${disabled.contains(Fagsystem.DAGPENGER)},
          "project": "default",
          "stale": true,
          "strategies": [
            {
              "name": "flexibleRollout",
              "constraints": [],
              "parameters": {
                "groupId": "utsjekk.stopp-iverksetting-dagpenger",
                "rollout": "100",
                "stickiness": "default"
              },
              "variants": []
            }
          ],
          "variants": [],
          "description": "Når denne er skrudd på stopper den iverksetting av utbetalinger for dagpenger",
          "impressionData": false
        },
        {
          "name": "utsjekk.stopp-iverksetting-tilleggsstonader",
          "type": "kill-switch",
          "enabled": ${disabled.contains(Fagsystem.TILLEGGSSTØNADER)},
          "project": "default",
          "stale": true,
          "strategies": [
            {
              "name": "flexibleRollout",
              "constraints": [],
              "parameters": {
                "groupId": "utsjekk.stopp-iverksetting-tilleggsstonader",
                "rollout": "100",
                "stickiness": "default"
              },
              "variants": []
            }
          ],
          "variants": [],
          "description": "Når denne er skrudd på stopper den iverksetting av utbetalinger for tilleggsstønader",
          "impressionData": false
        }
      ],
      "query": {
        "project": [
          "default"
        ],
        "environment": "development",
        "inlineSegmentConstraints": true
      },
      "meta": {
        "revisionId": 47,
        "etag": "\"6ef7ec5a:47\"",
        "queryHash": "6ef7ec5a"
      }
    }
""".trimIndent()