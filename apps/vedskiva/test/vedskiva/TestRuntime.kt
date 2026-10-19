package vedskiva

import libs.jdbc.Jdbc
import libs.jdbc.PostgresContainer
import libs.jdbc.migrateTemplate
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.truncate
import libs.kafka.*
import libs.utils.logger
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.SunHttpLoom
import org.http4k.server.asServer
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
            app // ensures producers/consumers are registered with the factory
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


    val app: HttpHandler by lazy {
        vedskiva(config, streams).also {
            Runtime.getRuntime().addShutdownHook(Thread {
                azure.close()
                peisschtappern.close()
                postgres.close()
            })
        }
    }

    inline fun <reified T : Any> post(path: String, body: T): Response =
        app(
            Request(Method.POST, path)
                .header("Authorization", "Bearer ${azure.generateToken()}")
                .with(VedskivaJson.autoBody<T>().toLens() of body)
        )

    fun reset() {
        app
        jdbc.truncate("vedskiva", Scheduled.TABLE_NAME, OppdragDao.table)
        streams.reset()
        PeisschtappernFake.response.clear()
    }
}

class AzureFake : AutoCloseable {
    private val server = routes(
        "/jwks" bind Method.GET to {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body(libs.auth.TEST_JWKS)
        },
        "/token" bind Method.POST to {
            Response(Status.OK)
                .header("Content-Type", "application/json")
                .body("""{"expires_in":3600,"access_token":"token"}""")
        }
    ).asServer(SunHttpLoom(0)).start()

    fun generateToken() = jwksGenerator.generate()

    override fun close() {
        server.stop()
    }

    val config by lazy {
        libs.auth.AzureConfig(
            tokenEndpoint = "http://localhost:${server.port()}/token".let(::URI).toURL(),
            jwks = "http://localhost:${server.port()}/jwks".let(::URI).toURL(),
            issuer = "test",
            clientId = "hei",
            clientSecret = "på deg"
        )
    }
    private val jwksGenerator = libs.auth.JwkGenerator(config.issuer, config.clientId)
}

class PeisschtappernFake : AutoCloseable {
    private val server = routes(
        "/api/messages" bind Method.GET to {
            if (response.isNotEmpty()) {
                Response(Status.OK).with(VedskivaJson.autoBody<Page>().toLens() of Page(response, response.size))
            } else {
                Response(Status.OK)
                    .header("Content-Type", "application/json")
                    .body(libs.utils.Resource.read("/may_5th.json"))
            }
        }
    ).asServer(SunHttpLoom(0)).start()

    override fun close() {
        server.stop()
    }

    companion object {
        val response = mutableListOf<Dao>()
    }

    val config by lazy {
        PeisschtappernConfig(
            host = "http://localhost:${server.port()}".let(::URI).toURL(),
            scope = "test"
        )
    }
}

