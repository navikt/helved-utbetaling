package utsjekk.oppdrag

import io.ktor.client.*
import libs.http.HttpClientFactory

class OppdragClient(
    private val client: HttpClient = HttpClientFactory.new()
) {
}