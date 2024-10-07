package utsjekk

import io.ktor.client.call.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libs.http.HttpClientFactory
import libs.utils.appLog
import java.net.InetAddress
import java.time.LocalDateTime

class LeaderElector(private val config: Config) {
    private val client = HttpClientFactory.new(logLevel = LogLevel.NONE)
    private var elected = false
    private var updatedAt: LocalDateTime? = null

    suspend fun isLeader(): Boolean {
        updatedAt?.let {
            // state is older than 1m
            if (it.plusMinutes(1).isBefore(LocalDateTime.now())) {
                update()
            }
        } ?: update() // get initial state
        return elected
    }

    private suspend fun update() {
        try {
            val response = client.get(config.electorUrl).body<LeaderElectionResponse>()
            val hostname = withContext(Dispatchers.IO) { InetAddress.getLocalHost() }.hostName
            // only log elected leader once.
            if (!elected && hostname == response.name) {
                appLog.info("I ($hostname) is elected as leader. This was decided on ${response.last_update}")
            }
            elected = hostname == response.name
            updatedAt = LocalDateTime.now()
        } catch (e: Exception) {
            elected = false
            updatedAt = LocalDateTime.now()
            appLog.warn("failed to elect leader", e)
        }
    }
}

data class LeaderElectionResponse(
    val name: String,
    val last_update: LocalDateTime,
)
