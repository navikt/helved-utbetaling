package vedskiva

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import libs.jdbc.Jdbc
import libs.jdbc.PostgresContainer
import libs.jdbc.migrateTemplate
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.truncate
import libs.kafka.*
import libs.ktor.KtorRuntime
import libs.utils.logger
import java.io.File
import java.net.URI
import javax.sql.DataSource

val testLog = logger("test")

object TestRuntime {
    private val migrationDirs = listOf(File("test/migrations"), File("migrations"))
    private val postgres: PostgresContainer by lazy {
        PostgresContainer(
            appname = "vedskiva",
            migrationDirs = migrationDirs,
            migrate = ::migrateTemplate,
        )
    }
    val azure: AzureFake by lazy { AzureFake() }
    val peisschtappern: PeisschtappernFake by lazy { PeisschtappernFake() }
    val kafka: StreamsMock
        get() {
            ktor // ensures producers/consumers are registered with the factory
            return streams
        }
    val streams: StreamsMock by lazy { StreamsMock() }
    val jdbc: DataSource by lazy { Jdbc.initialize(postgres.config) }
    val context: CoroutineDatasource by lazy { CoroutineDatasource(jdbc) }
    val config: Config by lazy {
        Config(
            kafka = StreamsConfig("test-application", "localhost:9092", SslConfig("", "", "")),
            jdbc = postgres.config,
            azure = azure.config,
            peisschtappern.config,
        )
    }


    val ktor: KtorRuntime<Config> by lazy {
        KtorRuntime<Config>(
            appName = "vedskiva",
            jsonConfig = VedskivaKotlinx,
            module = {
                vedskiva(
                    config,
                    streams,
                )
            },
            onClose = {
                reset()
                postgres.close()
            },
        )
    }

    fun reset() {
        jdbc.truncate("vedskiva", Scheduled.TABLE_NAME, OppdragDao.table)
        streams.reset()
        PeisschtappernFake.response.clear()
    }
}

class AzureFake {
    private val server = KtorRuntime<Nothing>(
        "vedskiva.azure", 
        jsonConfig = libs.kotlinx.KotlinxJson,
        AzureFake::azure
    )

    fun generateToken() = jwksGenerator.generate()

    companion object {
        fun azure(app: Application) {
            app.install(ContentNegotiation) { 
                json(libs.kotlinx.KotlinxJson)
            }
            app.routing {
                get("/jwks") {
                    call.respondText(libs.auth.TEST_JWKS)
                }

                post("/token") {
                    call.respond(libs.auth.AzureToken(3600, "token"))
                }
            }
        }
    }

    val config by lazy {
        libs.auth.AzureConfig(
            tokenEndpoint = "http://localhost:${server.port}/token".let(::URI).toURL(),
            jwks = "http://localhost:${server.port}/jwks".let(::URI).toURL(),
            issuer = "test",
            clientId = "hei",
            clientSecret = "på deg"
        )
    }
    private val jwksGenerator = libs.auth.JwkGenerator(config.issuer, config.clientId)
}

class PeisschtappernFake {
    private val server = KtorRuntime<Nothing>(
        "vedskiva.peisschtappern", 
        jsonConfig = libs.kotlinx.KotlinxJson,
        PeisschtappernFake::server
    )

    companion object {
        val response = mutableListOf<Dao>()
        fun server(app: Application) {
            app.install(ContentNegotiation) { 
                json(libs.kotlinx.KotlinxJson)
            }
            app.routing {
                get("/api/messages") {
                    if (response.isNotEmpty()) {
                        call.respond(Page(response, response.size))
                    } else {
                        call.respondText(libs.utils.Resource.read("/may_5th.json"), ContentType.Application.Json)
                    }
                }
            }
        }
    }

    val config by lazy {
        PeisschtappernConfig(
            host = "http://localhost:${server.port}".let(::URI).toURL(),
            scope = "test"
        )
    }
}

