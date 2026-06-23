package libs.ktor

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import libs.utils.logger

private val testLog = logger("test")

open class KtorRuntime<Config: Any>(
    val appName: String,
    val jsonConfig: Json,
    val module: Application.() -> Unit,
    val onClose: () -> Unit = {}, 
) {
    val ktor: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> = embeddedServer(Netty, port = 0) {
        module()
    }

    init {

        Runtime.getRuntime().addShutdownHook(Thread {
            testLog.info("Shutting down $appName TestRunner")
            onClose()
            ktor.stop(1000L, 5000L)
        })

        ktor.start(wait = false)
    }

    val httpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(jsonConfig)
            }
            defaultRequest {
                url("http://localhost:${ktor.engine.port}")
            }
        }
    }
    
    val port get() = ktor.engine.port
}

val NettyApplicationEngine.port: Int
    get() = runBlocking {
        resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
    }

