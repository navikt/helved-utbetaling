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
    fun post(brann: Brann) {
        runBlocking {
            client.post(config.slack.host.toString()) {
                contentType(ContentType.Application.Json)
                setBody(json(brann, config))
            }
        }
    }
}

private fun json(brann: Brann, config: Config): String = """{
  "blocks": [
    {
      "type": "header",
      "text": { "type": "plain_text", "text": "Flink alert :alert: (${config.nais.cluster})", "emoji": true }
    },
    {
      "type": "section",
      "text": { "type": "mrkdwn", "text": "Mangler kvittering for ${brann.key}" },
      "accessory": {
        "type": "button",
        "text": { "type": "plain_text", "text": "Peisen" },
        "url": "${config.peisen.host}/sak?sakId=${brann.sakId}&fagsystem=${brann.fagsystem}",
        "action_id": "button-action"
      }
    }
  ]
}""".trimIndent()
