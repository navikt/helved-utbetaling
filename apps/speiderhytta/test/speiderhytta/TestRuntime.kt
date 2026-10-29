package speiderhytta

import libs.jdbc.Jdbc
import libs.jdbc.PostgresContainer
import libs.jdbc.migrateTemplate
import libs.jdbc.concurrency.CoroutineDatasource
import libs.jdbc.truncate
import libs.utils.logger
import org.http4k.core.HttpHandler
import java.io.File
import javax.sql.DataSource

val testLog = logger("test")

/**
 * Test harness for speiderhytta. PostgreSQL via Testcontainers, the full
 * http4k app wired in. External clients (NAIS Deploy, GitHub, Prometheus) are
 * NOT spun up here — service-level tests instantiate the services directly
 * with fakes, and route tests call [http] directly.
 *
 * Migrations run as a side effect of [speiderhytta] starting. The
 * cloudsqliamuser role required by V1 only exists on NAIS, so a
 * `test/premigrations/V0_*` is prepended to the migration path for tests.
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

    val app: Speiderhytta by lazy { speiderhytta(config) }
    val http: HttpHandler get() = app.handler

    fun reset() {
        jdbc.truncate(
            "speiderhytta",
            "deployment",
            "incident",
            "poller_cursor",
            "slo_snapshot",
            "audit_task_comment",
            "audit_workflow_step",
            "audit_workflow_job",
            "audit_workflow_source",
            "audit_workflow_execution_commit",
            "audit_control_snapshot",
            "audit_workflow_execution",
            "audit_commit",
        )
    }
}
