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
