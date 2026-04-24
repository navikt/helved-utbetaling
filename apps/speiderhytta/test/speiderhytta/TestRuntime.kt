package speiderhytta

import io.ktor.client.HttpClient
import libs.jdbc.Jdbc
import libs.jdbc.PostgresContainer
import libs.jdbc.migrateTemplate
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.truncate
import libs.ktor.KtorRuntime
import libs.utils.logger
import java.io.File
import javax.sql.DataSource

val testLog = logger("test")

/**
 * Test harness for speiderhytta. PostgreSQL via Testcontainers, Ktor test
 * server with the full app wired in. External clients (NAIS Deploy, GitHub,
 * Prometheus) are NOT spun up here — service-level tests instantiate the
 * services directly with fakes.
 *
 * Migrations run as a side effect of [KtorRuntime] starting the [speiderhytta]
 * module — same pattern as `apps/urskog`. The cloudsqliamuser role required
 * by V1 only exists on NAIS, so a `test/premigrations/V0_*` is prepended to
 * the migration path for tests.
 */
object TestRuntime {
    private val migrationDirs = listOf(File("test/premigrations"), File("migrations"))
    private val postgres: PostgresContainer by lazy {
        PostgresContainer(
            appname = "speiderhytta",
            migrationDirs = migrationDirs,
            migrate = ::migrateTemplate,
        )
    }

    val config: Config by lazy {
        Config(
            jdbc = postgres.config,
            github = GithubConfig(appId = "1", installationId = "1", privateKeyPem = ""),
            slo = SloConfig(definitionsDir = File("test/slos")),
        )
    }

    val jdbc: DataSource by lazy { Jdbc.initialize(config.jdbc) }
    val context: CoroutineDatasource by lazy { CoroutineDatasource(jdbc) }

    val ktor: KtorRuntime<Config> by lazy {
        KtorRuntime<Config>(
            appName = "speiderhytta",
            module = { speiderhytta(config) },
            onClose = {
                reset()
                postgres.close()
            },
        )
    }

    val httpClient: HttpClient get() = ktor.httpClient

    fun reset() {
        jdbc.truncate(
            "speiderhytta",
            "deployment",
            "incident",
            "poller_cursor",
            "slo_snapshot",
        )
    }
}
