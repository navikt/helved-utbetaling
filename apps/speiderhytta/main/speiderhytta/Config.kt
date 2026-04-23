package speiderhytta

import libs.jdbc.JdbcConfig
import libs.utils.env
import java.io.File
import java.net.URI
import java.net.URL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class Config(
    val jdbc: JdbcConfig = JdbcConfig(
        url = env("DB_JDBC_URL"), // databaser provisjonert etter juni 2024 må bruke denne
        migrations = listOf(File("migrations")),
    ),
    val github: GithubConfig = GithubConfig(),
    val prometheus: PrometheusConfig = PrometheusConfig(),
    val slo: SloConfig = SloConfig(),
    val pollIntervals: PollIntervals = PollIntervals(),
    val apps: List<String> = HELVED_APPS,
)

/**
 * One source of deploy data — a single GitHub repo and the apps deployed
 * from it. We don't try to derive workflow file names by convention because
 * the convention isn't uniform across our repos:
 *
 *  - helved-utbetaling: one workflow file per app, named `<app>.yml`.
 *  - helved-peisen:     one shared workflow file `deploy.yaml` (note `.yaml`).
 *
 * Adding a new code repo is intentionally a code change — the list is short
 * and stable, so a JSON env var would be more friction than it's worth.
 */
data class CodeRepoConfig(
    val repo: String,
    val apps: Map<String, String>,
)

/**
 * GitHub App credentials and target repos.
 *
 * The same App is used against three repos:
 *  - `helved-utbetaling` and `helved-peisen` for deploy data (`actions: read`, `contents: read`).
 *  - `team-helved` for incident issues (`issues: read`) — that's where the team's kanban board lives.
 *
 * The App must be installed on all three repos. The same App ID +
 * installation ID work for every repo the App has access to (no per-repo
 * secrets).
 */
data class GithubConfig(
    val codeRepos: List<CodeRepoConfig> = listOf(
        CodeRepoConfig(
            repo = "navikt/helved-utbetaling",
            apps = HELVED_APPS.filter { it != "peisen" }.associateWith { "$it.yml" },
        ),
        CodeRepoConfig(
            repo = "navikt/helved-peisen",
            apps = mapOf("peisen" to "deploy.yaml"),
        ),
    ),
    val issueRepoOwner: String = "navikt",
    val issueRepoName: String = "team-helved",
    val apiUrl: URL = URI("https://api.github.com").toURL(),
    val appId: String = env("GITHUB_APP_ID"),
    val installationId: String = env("GITHUB_APP_INSTALLATION_ID"),
    val privateKeyPem: String = env("GITHUB_APP_PRIVATE_KEY"),
) {
    val issueRepo: String get() = "$issueRepoOwner/$issueRepoName"
}

data class PrometheusConfig(
    val url: URL = URI("https://prometheus.nav.cloud.nais.io/prometheus").toURL(),
)

data class SloConfig(
    val definitionsDir: File = File("/var/run/slos"),
)

data class PollIntervals(
    val deploy: Duration = 60.seconds,
    val incident: Duration = 60.seconds,
    val sloSnapshot: Duration = 300.seconds,
)

/**
 * Apps whose deployments and incidents we want to track DORA metrics for.
 *
 * speiderhytta is intentionally excluded from its own metrics. snickerboa is
 * included even though it currently has no `deploy-prod` job — the deploy
 * poller silently skips runs without one, so an empty deploy series is the
 * correct result rather than a hard exclusion.
 *
 * peisen is the frontend (lives in navikt/helved-peisen). It deploys from a
 * different workflow file convention (`deploy.yaml`) than the backend apps.
 * SLO loading for peisen is not yet implemented — `/slo/peisen` returns an
 * empty list.
 */
val HELVED_APPS: List<String> = listOf(
    "abetal",
    "branntaarn",
    "peisen",
    "peisschtappern",
    "simulering",
    "smokesignal",
    "snickerboa",
    "statistikkern",
    "urskog",
    "utsjekk",
    "vedskiva",
)
