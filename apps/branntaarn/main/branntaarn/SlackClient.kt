package branntaarn

import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import libs.http.HttpClientFactory

class SlackClient(
    private val config: Config,
    private val json: Json = libs.kotlinx.KotlinxJson,
    private val client: HttpClient = HttpClientFactory.new(json, LogLevel.ALL),
) {
    fun postAggregated(grouped: Map<String, List<Brann>>) {
        runBlocking {
            client.post(config.slack.host.toString()) {
                contentType(ContentType.Application.Json)
                setBody(jsonAggregated(grouped, config))
            }
        }
    }

    fun postPendingMismatches(mismatches: List<PendingMismatch>) {
        runBlocking {
            client.post(config.slack.host.toString()) {
                contentType(ContentType.Application.Json)
                setBody(jsonPendingMismatches(mismatches, config))
            }
        }
    }
}

private fun jsonAggregated(
    grouped: Map<String, List<Brann>>,
    config: Config
): String {
    val totalCount = grouped.values.sumOf { it.size }
    val fagsystemCount = grouped.size
    val fagsystemText = if (fagsystemCount == 1) "fagsystem" else "fagsystems"
    
    val blocks = buildList {
        // Header
        add("""
        {
          "type": "header",
          "text": {
            "type": "plain_text",
            "text": "${emoji(config)} Branntårn Alert (${config.nais.cluster})",
            "emoji": true
          }
        }
        """.trimIndent())
        
        // Summary
        add("""
        {
          "type": "section",
          "text": {
            "type": "mrkdwn",
            "text": "*$totalCount missing kvitteringer across $fagsystemCount $fagsystemText*"
          }
        }
        """.trimIndent())
        
        add("""{"type": "divider"}""")
        
        // Per-fagsystem sections (sorted alphabetically)
        grouped.entries.sortedBy { it.key }.forEach { (fagsystem, branner) ->
            val sakIds = branner.map { it.sakId }
            val displayLimit = 20
            val sakIdText = if (sakIds.size <= displayLimit) {
                sakIds.joinToString(", ")
            } else {
                val shown = sakIds.take(displayLimit).joinToString(", ")
                val remaining = sakIds.size - displayLimit
                "$shown _(+$remaining more not shown)_"
            }
            
            add("""
            {
              "type": "section",
              "text": {
                "type": "mrkdwn",
                "text": "*$fagsystem* - ${branner.size} missing\nSak: $sakIdText"
              }
            }
            """.trimIndent())
            
            add("""{"type": "divider"}""")
        }
    }
    
    return """{
  "channel": "team-hel-ved-alerts",
  "blocks": [
    ${blocks.joinToString(",\n    ")}
  ]
}"""
}

private fun emoji(config: Config): String {
    return when (config.nais.cluster) {
        "prod-gcp" -> "🔥"
        else -> ""
    }
}

private fun jsonPendingMismatches(
    mismatches: List<PendingMismatch>,
    config: Config
): String {
    val grouped = mismatches.groupBy { it.fagsystem ?: "ukjent" }
    val totalCount = mismatches.size
    val fagsystemCount = grouped.size
    val fagsystemText = if (fagsystemCount == 1) "fagsystem" else "fagsystems"

    val blocks = buildList {
        add("""
        {
          "type": "header",
          "text": {
            "type": "plain_text",
            "text": "${emoji(config)} Pending Mismatch Alert (${config.nais.cluster})",
            "emoji": true
          }
        }
        """.trimIndent())

        add("""
        {
          "type": "section",
          "text": {
            "type": "mrkdwn",
            "text": "*$totalCount pending/utbetaling mismatch(es) across $fagsystemCount $fagsystemText*\nPeriodene i pending og utbetaling samsvarer ikke."
          }
        }
        """.trimIndent())

        add("""{"type": "divider"}""")

        grouped.entries.sortedBy { it.key }.forEach { (fagsystem, items) ->
            val displayLimit = 20
            val uidText = if (items.size <= displayLimit) {
                items.joinToString(", ") { it.uid }
            } else {
                val shown = items.take(displayLimit).joinToString(", ") { it.uid }
                val remaining = items.size - displayLimit
                "$shown _(+$remaining more not shown)_"
            }

            add("""
            {
              "type": "section",
              "text": {
                "type": "mrkdwn",
                "text": "*$fagsystem* - ${items.size} mismatch(es)\nUID: $uidText"
              }
            }
            """.trimIndent())

            add("""{"type": "divider"}""")
        }
    }

    return """{
  "channel": "team-hel-ved-alerts",
  "blocks": [
    ${blocks.joinToString(",\n    ")}
  ]
}"""
}
