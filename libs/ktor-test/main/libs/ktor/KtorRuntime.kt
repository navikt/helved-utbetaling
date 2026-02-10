package libs.ktor

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import libs.utils.logger

private val testLog = logger("test")

open class KtorRuntime<Config: Any>(
    val appName: String,
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
                jackson {
                    registerModule(JavaTimeModule())
                    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                }
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


