# AGENTS.md - Coding Agent Instructions for helved-utbetaling

## Project Overview

Multi-module Kotlin monorepo for NAV (Norwegian government) payment system.
Ktor-based (NOT Spring). Runs on NAIS (Kubernetes on GCP). 9 apps + 15 libs + shared models.

## Documentation Access

This project uses **Context7 MCP** for accessing up-to-date documentation. The configuration is in `opencode.json`.

### Available Documentation Sources

Context7 automatically provides current docs for:
- **NAIS platform**: https://docs.nais.io/ (NAV's Kubernetes platform)
- **NAV Security**: https://sikkerhet.nav.no/ (security playbook)
- **Ktor 3.x**: https://ktor.io/docs/ (web framework)
- **Kafka Streams**: https://kafka.apache.org/documentation/streams/
- **Kotlin**: https://kotlinlang.org/docs/

### Setup

API key is stored in `~/.bash_profile` as `CONTEXT7_API_KEY`. After updating `.bash_profile`:
```sh
source ~/.bash_profile
```

### Usage in Prompts

When asking code-related questions, Context7 will automatically fetch current documentation.
You can explicitly invoke it by adding `use context7` to your prompt, or reference specific libraries:
```
Implement auth with Ktor. use library /ktor/ktor for API and docs.
```

## Build System

- **Gradle 8.13** (Kotlin DSL), **Kotlin 2.3.10**, **JVM 21**
- Compiler: `allWarningsAsErrors = true`, `extraWarnings = true` -- all warnings are errors

### Commands

```sh
# Build everything
./gradlew build

# Run all tests
./gradlew test --continue

# Run tests for a specific module
./gradlew apps:utsjekk:test
./gradlew apps:abetal:test
./gradlew libs:jdbc:test

# Run a single test class
./gradlew apps:utsjekk:test --tests "utsjekk.iverksetting.IverksettingRouteTest"

# Run a single test method (use the backtick-quoted name)
./gradlew apps:utsjekk:test --tests "utsjekk.iverksetting.IverksettingRouteTest.start iverksetting av vedtak uten utbetaling"

# Build fat JAR for an app
./gradlew apps:utsjekk:buildFatJar
```

CI runs: `./gradlew test --continue --no-daemon`

### Non-Standard Source Layout

Sources are NOT in `src/main/kotlin`. The convention is:
- **Main sources**: `<module>/main/<package>/`
- **Test sources**: `<module>/test/<package>/`
- **Resources**: alongside Kotlin files in `main/`
- **DB migrations**: `<module>/migrations/`

Example: `apps/utsjekk/main/utsjekk/Utsjekk.kt`, `apps/utsjekk/test/utsjekk/IverksettingRouteTest.kt`

## Testing

- **JUnit 5** via `kotlin("test")` -- use `kotlin.test.*` assertions (`assertEquals`, `assertNotNull`)
- Tests run in parallel at class level (same thread within a class, concurrent across classes)
- **No mocking frameworks** -- uses real infrastructure via Testcontainers and custom fakes

### Test Infrastructure

| Library | Purpose |
|---------|---------|
| `libs:jdbc-test` | PostgreSQL via Testcontainers |
| `libs:kafka-test` | Kafka Streams test utils (`StreamsMock`) |
| `libs:auth-test` | JWT test token generation |
| `libs:ktor-test` | Ktor test host + CIO client |
| `libs:mq-test` | IBM MQ test utilities |

### Reusable Testcontainers

Containers are reused between test runs. If containers are stopped and tests fail with 409:
```sh
docker start mq postgres
```

### Test Patterns

- **Test names**: backtick-quoted descriptive sentences (Norwegian or English):
  ```kotlin
  @Test fun `start iverksetting av vedtak uten utbetaling`() = runTest(TestRuntime.context) { ... }
  ```
- **`TestRuntime` singleton** per app provides fakes, config, Kafka mock, DB datasource, Ktor test server
- **`TestData` object** with factory methods for creating test fixtures with sensible defaults
- **`DataSource.await { }` pattern** for polling DB in async tests
- **`runTest(TestRuntime.context)`** wraps coroutine tests with the correct DB context

## Code Style

### Formatting

- 4-space indent, spaces (no tabs)
- K&R/Egyptian bracket style (opening brace on same line)
- No trailing commas (explicitly disabled)
- No enforced line length limit
- ktlint configured via `.editorconfig` with most rules disabled; enabled rules:
  indent, spacing, import-ordering, no-consecutive-blank-lines, argument-list-wrapping,
  parameter-list-wrapping, no-blank-line-before-rbrace, value-argument-comment-spacing
- No detekt configured

### Imports

- Wildcard imports are common: `io.ktor.server.application.*`, `models.*`
- Explicit imports also used; no strict rule
- Ordering: external libs, internal libs (`libs.*`), models (`models.*`), app-local, java stdlib
- No blank lines between import groups

### Naming Conventions

- **Packages**: single lowercase word matching the module: `utsjekk`, `abetal`, `libs.jdbc`
- **Classes**: PascalCase: `IverksettingService`, `UtbetalingDao`, `ApiError`
- **Functions**: camelCase: `iverksett`, `valider`, `createTopology`
- **Variables**: camelCase: `oppdragProducer`, `utbetalingService`
- **Files**: match the primary class/function: `Utsjekk.kt`, `Config.kt`, `Routing.kt`
- **Norwegian domain terms** used extensively: `Iverksetting`, `Utbetaling`, `Stønadstype`,
  `vedtakstidspunkt`, `sakId`, `behandlingId`, `Fagsakdetaljer`, `Søker`
- **Value classes** with `@JvmInline`: `SakId`, `BehandlingId`, `Personident`, `UtbetalingId`
- **Sealed interfaces** for type hierarchies: `Stønadstype`, `Result`, `TokenType`

### Error Handling

- **`ApiError`** data class extends `RuntimeException` with `statusCode`, `msg`, `doc`, `system`
- **Helper functions** that throw `ApiError` and return `Nothing`:
  `badRequest()`, `notFound()`, `conflict()`, `locked()`, `unauthorized()`, `forbidden()`
- **`DocumentedErrors`** enum links each error to a documentation URL
- **StatusPages plugin** catches `ApiError` globally and maps to HTTP responses
- **Custom `Result<V, E>` type** (in `libs.utils` and `models`) with `Ok`/`Err`, `unwrap()`,
  `map()`, `fold()`, `onSuccess()`, `onFailure()`
- **`Result.catch { }` pattern** wraps domain logic in Kafka Streams processing

### Coroutine Patterns

- **`withContext(Jdbc.context)`** for DB operations (custom `CoroutineDatasource` element)
- **`transaction { }`** suspending function for DB connection management
- **`runBlocking`** only at startup for migrations
- Kafka Streams topology is NOT coroutine-based

## Architecture Patterns

### App Structure

Each app follows this pattern:
1. `fun main()` sets uncaught exception handler, calls `embeddedServer(module = Application::appName)`
2. `fun Application.appName()` wires dependencies and installs Ktor plugins
3. Routing via extension functions on `Route`
4. `Config` data class with defaults from `env()` calls

### Dependency Injection

No DI framework. Manual constructor injection wired in the app entry-point function.

### Database

- PostgreSQL + HikariCP, custom migration system (not Flyway/Liquibase)
- `Dao<T>` interface with `table`, `from(ResultSet)`, generic `query()` and `update()` methods
- Companion objects implement `Dao<T>`

### Kafka

- Custom `topology { }` DSL wrapping Kafka Streams
- `Topic` objects defined in a `Topics` object per app
- `Table` and `Store` abstractions for state stores
- `consume()`, `.map()`, `.branch()`, `.produce()` operations

### Logging

- **`appLog`** for application logs (no sensitive data)
- **`secureLog`** for sensitive data -- NEVER log PII to `appLog`
- Logback + logstash-logback-encoder for structured JSON logging

### Modules

When adding dependencies between modules, use Gradle project references:
```kotlin
dependencies {
    implementation(project(":libs:jdbc"))
    implementation(project(":models"))
    testImplementation(project(":libs:jdbc-test"))
}
```

Dependency versions are declared inline as `val` in each module's `build.gradle.kts` (no version catalog).
