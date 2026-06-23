# AGENTS.md - helved-utbetaling

Multi-module Kotlin monorepo. NAV payment system. Ktor (NOT Spring). NAIS (K8s/GCP). 11 apps + 15 libs + shared models.

## Build

- Gradle 8.13 (Kotlin DSL), Kotlin 2.3.10, JVM 21
- Compiler: `allWarningsAsErrors = true`, `extraWarnings = true`

```sh
./gradlew build                          # all
./gradlew test --continue                # all tests
./gradlew apps:utsjekk:test              # module tests
./gradlew apps:utsjekk:test --tests "utsjekk.iverksetting.IverksettingRouteTest"  # class
./gradlew apps:utsjekk:test --tests "utsjekk.iverksetting.IverksettingRouteTest.start iverksetting av vedtak uten utbetaling"  # method
./gradlew apps:utsjekk:buildFatJar       # fat JAR
```

CI: `./gradlew test --continue --no-daemon`

### Source Layout (non-standard)

- Main: `<module>/main/<package>/`
- Test: `<module>/test/<package>/`
- Resources: alongside Kotlin in `main/`
- Migrations: `<module>/migrations/`

Example: `apps/utsjekk/main/utsjekk/Utsjekk.kt`

## Testing

JUnit 5, `kotlin.test.*` assertions, no mocking — Testcontainers + custom fakes. Parallel at class level. Load `testing` skill for patterns (TestRuntime, runTest, DataSource.await, TestData).

Containers reused. If stopped → `docker start mq postgres`

## Code Style

- 4-space indent, K&R braces, no trailing commas
- ktlint via `.editorconfig` (most rules disabled)
- Wildcard imports common
- Norwegian domain terms: Iverksetting, Utbetaling, Stønadstype, sakId, behandlingId, Søker
- Value classes (`@JvmInline`): SakId, BehandlingId, Personident, UtbetalingId
- Sealed interfaces for type hierarchies

### Error Handling

`ApiError` + helpers (`badRequest()`, `notFound()`, `conflict()`, etc.) → StatusPages catches globally. Custom `Result<V,E>` with Ok/Err in libs.utils and models.

### Coroutines

`withContext(jdbcCtx)` for DB ops. `transaction { }` suspending. `runBlocking` only at startup. Kafka Streams NOT coroutine-based.

## Architecture

- `fun main()` → `embeddedServer(module = Application::appName)` → wires deps + plugins
- Routing via `Route` extension functions
- `Config` data class with `env()` defaults
- No DI framework — manual constructor injection
- DB: PostgreSQL + HikariCP, custom migrations, `Dao<T>` interface. Load `database` skill.
- Kafka: custom `topology {}` DSL. Load `kafka-topology` skill.
- Logging: `appLog` (no PII), `secureLog` (sensitive). Logback + logstash-encoder.

## Skills

| Skill | When |
|-------|------|
| `libs-reference` | Code touching /libs |
| `readable-code` | Writing/refactoring any .kt file |
| `kafka-topology` | Kafka Streams topologies |
| `testing` | Writing tests |
| `database` | Migrations, DAOs, transactions |
| `ktor-routing` | Routes, auth, StatusPages |
| `ripgrep` | Searching code/files |

## Modules

Domain docs: `apps/AGENTS.md`. Deps use project refs:
```kotlin
dependencies {
    implementation(project(":libs:jdbc"))
    testImplementation(project(":libs:jdbc-test"))
}
```
Versions declared inline (no version catalog).

## Loki Logs

Query logs via `logcli`:

```sh
logcli query '{service_name="<app>", k8s_cluster_name="prod"}' --addr=https://loki.nav.cloud.nais.io --org-id=tenant --bearer-token="$(gcloud auth print-access-token)" --limit=50
```

Services: `speiderhytta`, `peisschtappern`, `abetal`, `utsjekk`, `urskog`, `branntaarn`, `helved-peisen`, `logs`, `smokesignal`, `statistikkern`, `simulering`, `vedskiva`, `ws-proxy`

Available labels: `service_name`, `service_namespace`, `k8s_cluster_name`, `k8s_pod_name`, `k8s_node_name`, `k8s_container_name`, `detected_level`, `deployment_environment`

Clusters: `prod`, `dev`, `prod-fss`, `dev-fss`

Useful patterns:
```sh
# Filter by text
logcli query '{service_name="utsjekk", k8s_cluster_name="prod"} |= "ERROR"' --addr=https://loki.nav.cloud.nais.io --org-id=tenant --bearer-token="$(gcloud auth print-access-token)" --limit=20

# Time range (RFC3339 or relative)
logcli query '{service_name="utsjekk", k8s_cluster_name="prod"}' --addr=https://loki.nav.cloud.nais.io --org-id=tenant --bearer-token="$(gcloud auth print-access-token)" --from="2h ago" --limit=50

# JSON field extraction
logcli query '{service_name="utsjekk", k8s_cluster_name="prod"} | json | level="ERROR"' --addr=https://loki.nav.cloud.nais.io --org-id=tenant --bearer-token="$(gcloud auth print-access-token)" --limit=20

# Dev cluster
logcli query '{service_name="utsjekk", k8s_cluster_name="dev"}' --addr=https://loki.nav.cloud.nais.io --org-id=tenant --bearer-token="$(gcloud auth print-access-token)" --limit=20
```

Logs are JSON-structured (logstash-encoder). Key fields: `message`, `level`, `logger_name`, `stack_trace`.

## Tempo Traces

Query production traces via `tempo-cli-arm64`:

```sh
tempo-cli-arm64 query api search --org-id helved --header "Authorization=Bearer $(gcloud auth print-access-token)" --secure tempo.prod-gcp.nav.cloud.nais.io '{resource.service.name="<app>"}' now-15m now
```

Environments:
- Prod: `tempo.prod-gcp.nav.cloud.nais.io`
- Dev: `tempo.dev-gcp.nav.cloud.nais.io`

Useful patterns:
```sh
# Search by service
tempo-cli-arm64 query api search --org-id helved --header "Authorization=Bearer $(gcloud auth print-access-token)" --secure tempo.prod-gcp.nav.cloud.nais.io '{resource.service.name="utsjekk"}' now-1h now

# Search errors
tempo-cli-arm64 query api search --org-id helved --header "Authorization=Bearer $(gcloud auth print-access-token)" --secure tempo.prod-gcp.nav.cloud.nais.io '{status=error}' now-1h now

# Fetch specific trace
tempo-cli-arm64 query api trace-id --org-id helved --header "Authorization=Bearer $(gcloud auth print-access-token)" https://tempo.prod-gcp.nav.cloud.nais.io <trace-id>
```

Note: Uses same GCP token as Loki (`gcloud auth print-access-token`). `search` uses `--secure` flag + hostname only (no `https://`). `trace-id` uses full URL (`https://...`) — no `--secure` flag.
