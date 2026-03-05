package branntaarn

import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import libs.http.HttpClientFactory

class SlackClient(
    private val config: Config,
    private val client: HttpClient = HttpClientFactory.new(LogLevel.ALL),
) {
    fun postAggregated(grouped: Map<String, List<Brann>>) {
        runBlocking {
            client.post(config.slack.host.toString()) {
                contentType(ContentType.Application.Json)
                setBody(jsonAggregated(grouped, config))
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
