---
name: readable-code
description: Write readable and maintainable code following helved-utbetaling patterns
license: MIT
compatibility: opencode
metadata:
  audience: ai-assistant
  language: kotlin
  framework: ktor
  domain: nav-payment-system
---

## What I do

I guide you to write readable and maintainable Kotlin code that follows the established patterns in the helved-utbetaling codebase.

## When to use me

Load this skill when you are writing, refactoring, or reviewing code in this project.

> **Prerequisites:** Root `AGENTS.md` covers naming conventions, error handling patterns, testing patterns, and architecture. This skill provides **concrete code examples** that complement those rules.

---

## 1. Code Structure

### Function Length

Target 10-30 lines per function. Use early returns for clarity. Extract complex logic into helper functions.

```kotlin
// Good: Clear flow, early returns, focused logic
fun branntaarn(
    config: Config = Config(),
    now: LocalDateTime = LocalDateTime.now(),
) {
    if (now.toLocalDate().erHelligdag() || now.hour < 6 || now.hour > 21) return

    val peisschtappern = PeisschtappernClient(config)
    val slack = SlackClient(config)

    val branner = peisschtappern.branner()
        .filter { brann -> brann.timeout.isBefore(now) }

    if (branner.isEmpty()) return

    val grouped = branner.groupBy { it.fagsystem }

    slack.postAggregated(grouped)

    branner.forEach(peisschtappern::slukk)
}

// Bad: Nested conditions, unclear flow
fun branntaarn(
    config: Config = Config(),
    now: LocalDateTime = LocalDateTime.now(),
) {
    if (!now.toLocalDate().erHelligdag() && now.hour >= 6 && now.hour <= 21) {
        val peisschtappern = PeisschtappernClient(config)
        val slack = SlackClient(config)
        val branner = peisschtappern.branner().filter { it.timeout.isBefore(now) }
        if (branner.isNotEmpty()) {
            val grouped = branner.groupBy { it.fagsystem }
            slack.postAggregated(grouped)
            for (brann in branner) {
                peisschtappern.slukk(brann)
            }
        }
    }
}
```

### File Organization

Public API at the top, private helpers below. Group related functions together.

```kotlin
// Public API first, private helpers below
package branntaarn

class SlackClient(
    private val config: Config,
    private val client: HttpClient = HttpClientFactory.new(LogLevel.ALL),
) {
    fun postAggregated(grouped: Map<String, List<Brann>>) {
        runBlocking {
            client.post(config.slack.host.toString()) {
                contentType(ContentType.Application.Json)
                setBody(jsonAggregated(grouped, config))
            }
        }
    }
}

private fun jsonAggregated(
    grouped: Map<String, List<Brann>>,
    config: Config
): String { /* ... */ }

private fun emoji(config: Config): String = when (config.nais.cluster) {
    "prod-gcp" -> ":fire:"
    else -> ""
}
```

### Clear Data Flow

Keep transformation pipelines readable with chained operations.

```kotlin
// Good: Clear transformation pipeline
val sakIdText = if (sakIds.size <= displayLimit) {
    sakIds.joinToString(", ")
} else {
    val shown = sakIds.take(displayLimit).joinToString(", ")
    val remaining = sakIds.size - displayLimit
    "$shown _(+$remaining more not shown)_"
}
```

---

## 2. Documentation

Use comments to explain WHY, not WHAT. Document business rules and non-obvious Norwegian terms.

```kotlin
// Good: Explains business rule
// Skip alerts outside operational hours (06-21, weekdays only, excluding holidays)
if (now.toLocalDate().erHelligdag() || now.hour < 6 || now.hour > 21) return

// Good: Clarifies Norwegian term
// Slukk (extinguish) - Delete the timer from peisschtappern
branner.forEach(peisschtappern::slukk)
```

Prefer self-documenting code over comments:

```kotlin
// Good: Self-documenting with named boolean
val isOutsideOperationalHours = now.toLocalDate().erHelligdag()
    || now.hour < 6
    || now.hour > 21

if (isOutsideOperationalHours) return
```

---

## 3. Error Handling Examples

### ApiError

```kotlin
if (sakId.isEmpty()) {
    badRequest("sakId cannot be empty", DocumentedErrors.INVALID_SAK_ID)
}

val utbetaling = dao.findById(utbetalingId)
    ?: notFound("Utbetaling $utbetalingId not found")
```

### Result Type

```kotlin
val result = Result.catch {
    oppdragMapper.readValue(value)
}.onFailure { error ->
    appLog.warn("Failed to parse oppdrag: ${error.message}")
}
```

### Null Handling

```kotlin
// Good: Explicit null handling with logging
val oppdrag = oppdragMapper.readValue(value) ?: run {
    appLog.warn("Failed to parse oppdrag for key $key")
    return stopTimer(key)
}

// Bad: Silent failure
val oppdrag = oppdragMapper.readValue(value) ?: return
```

---

## 4. Dependency Injection

Constructor injection with default parameters. No DI framework. Wire in app entry point.

```kotlin
// Good: Clear dependencies, testable with defaults
class SlackClient(
    private val config: Config,
    private val client: HttpClient = HttpClientFactory.new(LogLevel.ALL),
) { /* ... */ }

// Good: App wiring in entry point
fun Application.utsjekk() {
    val config = Config()
    val datasource = Jdbc.initialize(config.jdbc)
    val oppdragProducer = OppdragProducer(config.kafka)
    val iverksettingService = IverksettingService(datasource, oppdragProducer)

    routing {
        iverksettingRoutes(iverksettingService)
    }
}

// Bad: Hidden dependencies, hard to test
class SlackClient {
    private val config = Config()  // Hard to test
    private val client = HttpClient(CIO)  // Can't mock
}
```

---

## 5. Kotlin-Specific Patterns

### Data Classes

```kotlin
data class Brann(
    val key: String,
    val timeout: LocalDateTime,
    val sakId: String,
    val fagsystem: String,
)

data class Config(
    val azure: AzureConfig = AzureConfig(),
    val slack: SlackConfig = SlackConfig(),
    val nais: NaisConfig = NaisConfig(),
)
```

### Expression Bodies

```kotlin
private fun emoji(config: Config): String = when (config.nais.cluster) {
    "prod-gcp" -> ":fire:"
    else -> ""
}

fun shouldSkipProcessing(now: LocalDateTime): Boolean =
    now.toLocalDate().erHelligdag()
        || now.hour < 6
        || now.hour > 21
```

### Collection Operations

```kotlin
// Good: Functional chaining
val grouped = branner
    .filter { brann -> brann.timeout.isBefore(now) }
    .groupBy { it.fagsystem }

// Good: buildList for construction
val blocks = buildList {
    add("""{"type": "header"}""")
    grouped.entries.sortedBy { it.key }.forEach { (fagsystem, branner) ->
        add("""{"type": "section", "text": "$fagsystem - ${branner.size}"}""")
    }
}
```

### String Formatting

```kotlin
// Good: String templates
val message = "*$totalCount missing kvitteringer across $fagsystemCount fagsystems*"

// Good: Triple-quoted string with trimIndent
val json = """
{
  "type": "header",
  "text": {
    "type": "plain_text",
    "text": "Branntaarn Alert (${config.nais.cluster})"
  }
}
""".trimIndent()
```
