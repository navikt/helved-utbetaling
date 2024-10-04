package utsjekk

import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libs.http.HttpClientFactory
import libs.utils.appLog
import java.net.InetAddress

class LeaderElector(private val config: Config) {
    private val client = HttpClientFactory.new()

    suspend fun isLeader(): Boolean {
        return try {
            val elected = client.get(config.electorUrl).bodyAsText()
            val hostname = withContext(Dispatchers.IO) { InetAddress.getLocalHost() }.hostName
            appLog.info("Hostname:$hostname Elected:$elected")
            return elected == hostname
        } catch (e: Exception) {
            appLog.warn("no leader elected", e)
            false
        }
    }
}
