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

I guide you to write readable and maintainable Kotlin code that follows the established patterns in the helved-utbetaling codebase. I help you balance Norwegian domain terminology with English technical terms, apply consistent error handling, structure code for clarity, and follow testing conventions.

## When to use me

Load this skill when you are:
- Writing new functions, classes, or modules
- Refactoring existing code for readability
- Creating test cases
- Handling errors or implementing logging
- Reviewing code structure or naming
- Making changes to any application or library in this project

---

## 1. Naming Conventions

### Function Naming

Use verb-first descriptive names. Mix Norwegian domain terms naturally with English technical terms.

**Norwegian domain terms** (business concepts):
- `iverksett()` - execute/implement a payment decision
- `slukk()` - extinguish/delete a timer
- `avstem()` - reconcile accounts

**English technical terms** (implementation details):
- `postAggregated()` - send aggregated data
- `createTopology()` - build Kafka topology
- `sendOppdrag()` - send payment order

**Examples:**

```kotlin
// ✅ Good: Clear intent, appropriate Norwegian term
fun slukk(brann: Brann) {
    runBlocking {
        client.delete("${config.host}/api/brann/${brann.key}")
    }
}

// ❌ Bad: Unclear abbreviation
fun del(b: Brann) { ... }

// ✅ Good: Descriptive English technical term
fun postAggregated(grouped: Map<String, List<Brann>>) {
    runBlocking {
        client.post(config.slack.host.toString()) {
            setBody(jsonAggregated(grouped, config))
        }
    }
}

// ❌ Bad: Generic name, unclear purpose
fun post(data: Map<String, List<Brann>>) { ... }
```

### Variable Naming

Use descriptive camelCase names. Leverage value classes for type safety.

```kotlin
// ✅ Good: Descriptive, clear purpose
val oppdragProducer: KafkaProducer<String, Oppdrag>
val utbetalingService: UtbetalingService
val sakId: SakId  // Value class for type safety

// ❌ Bad: Abbreviations, unclear
val op: KafkaProducer<String, Oppdrag>
val svc: UtbetalingService
val id: String  // Primitive obsession
```

### Class and File Naming

Match the filename to the primary class. Use consistent suffixes.

**Patterns:**
- `*Dao` - Data access objects: `OppdragDao`, `TimerDao`
- `*Client` - HTTP clients: `SlackClient`, `PeisschtappernClient`
- `*Service` - Business logic: `IverksettingService`, `AvstemmingService`
- `*Factory` - Object creation: `HttpClientFactory`, `AvstemmingFactory`

```kotlin
// ✅ Good: File IverksettingService.kt
class IverksettingService(
    private val dao: IverksettingDao,
    private val producer: KafkaProducer<String, Oppdrag>
) { ... }

// ✅ Good: File SlackClient.kt
class SlackClient(
    private val config: Config,
    private val client: HttpClient = HttpClientFactory.new(LogLevel.ALL)
) { ... }
```

---

## 2. Code Structure

### Function Length

Target 10-30 lines per function. Use early returns for clarity. Extract complex logic into helper functions.

```kotlin
// ✅ Good: Clear flow, early returns, focused logic
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

// ❌ Bad: Nested conditions, unclear flow
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

Structure files with public API at the top, private helpers below. Group related functions together.

```kotlin
// ✅ Good: Public API first, private helpers below
package branntaarn

class SlackClient(
    private val config: Config,
    private val client: HttpClient = HttpClientFactory.new(LogLevel.ALL),
) {
    // Public API
    fun postAggregated(grouped: Map<String, List<Brann>>) {
        runBlocking {
            client.post(config.slack.host.toString()) {
                contentType(ContentType.Application.Json)
                setBody(jsonAggregated(grouped, config))
            }
        }
    }
}

// Private helpers
private fun jsonAggregated(
    grouped: Map<String, List<Brann>>,
    config: Config
): String {
    // Implementation...
}

private fun emoji(config: Config): String {
    return when (config.nais.cluster) {
        "prod-gcp" -> "🔥"
        else -> ""
    }
}
```

### Clear Data Flow

Keep transformation pipelines readable with chained operations.

```kotlin
// ✅ Good: Clear transformation pipeline
val sakIdText = if (sakIds.size <= displayLimit) {
    sakIds.joinToString(", ")
} else {
    val shown = sakIds.take(displayLimit).joinToString(", ")
    val remaining = sakIds.size - displayLimit
    "$shown _(+$remaining more not shown)_"
}

// ❌ Bad: Unclear with side effects
var sakIdText = ""
if (sakIds.size <= displayLimit) {
    for (id in sakIds) {
        sakIdText += id + ", "
    }
    sakIdText = sakIdText.dropLast(2)
} else {
    for (i in 0 until displayLimit) {
        sakIdText += sakIds[i] + ", "
    }
    sakIdText += "_(+${sakIds.size - displayLimit} more not shown)_"
}
```

---

## 3. Documentation

### When to Use Comments

Use comments to explain WHY, not WHAT. Document business rules, non-obvious Norwegian terms, and complex coordination patterns.

```kotlin
// ✅ Good: Explains business rule
// Skip alerts outside operational hours (06-21, weekdays only, excluding holidays)
if (now.toLocalDate().erHelligdag() || now.hour < 6 || now.hour > 21) return

// ✅ Good: Clarifies Norwegian term
// Slukk (extinguish) - Delete the timer from peisschtappern
branner.forEach(peisschtappern::slukk)

// ❌ Bad: States the obvious
// Return if hour is less than 6
if (now.hour < 6) return

// ❌ Bad: Redundant with code
// Check if list is empty
if (branner.isEmpty()) return
```

### Self-Documenting Code

Prefer descriptive names over comments. Extract complex conditions into named variables or functions.

```kotlin
// ✅ Good: Self-documenting with named boolean
val isOutsideOperationalHours = now.toLocalDate().erHelligdag() 
    || now.hour < 6 
    || now.hour > 21

if (isOutsideOperationalHours) return

// ❌ Bad: Requires comment to understand
if (x || y < 6 || y > 21) return  // Skip if outside hours

// ✅ Good: Descriptive function name
fun shouldSkipProcessing(now: LocalDateTime): Boolean {
    return now.toLocalDate().erHelligdag() || now.hour < 6 || now.hour > 21
}

if (shouldSkipProcessing(now)) return
```

---

## 4. Error Handling

### ApiError Pattern

Use helper functions from the codebase: `badRequest()`, `notFound()`, `conflict()`, `locked()`, `unauthorized()`, `forbidden()`.

Include documentation links via `DocumentedErrors` enum. Provide actionable error messages.

```kotlin
// ✅ Good: Clear error with helper function
if (sakId.isEmpty()) {
    badRequest("sakId cannot be empty", DocumentedErrors.INVALID_SAK_ID)
}

// ✅ Good: Contextual error message
val utbetaling = dao.findById(utbetalingId) 
    ?: notFound("Utbetaling $utbetalingId not found")
```

### Result Type

Use `Result<V, E>` for domain operations. Chain operations with `map()`, `fold()`, `onSuccess()`, `onFailure()`.

```kotlin
// ✅ Good: Result type for domain logic
fun valider(oppdrag: Oppdrag): Result<Oppdrag, ValidationError> {
    return Result.catch {
        if (oppdrag.sakId.isEmpty()) {
            return Result.Err(ValidationError("sakId cannot be empty"))
        }
        Result.Ok(oppdrag)
    }
}

// ✅ Good: Wrapping risky operations
val result = Result.catch { 
    oppdragMapper.readValue(value) 
}.onFailure { error ->
    appLog.warn("Failed to parse oppdrag: ${error.message}")
}
```

### Logging

Use `appLog` for non-sensitive operational logs. Use `secureLog` for PII or sensitive data.

```kotlin
// ✅ Good: Clear operational log without PII
appLog.info("Processing oppdrag with key $key")
appLog.warn("Failed to connect to peisschtappern")
appLog.error("Uhåndtert feil ${e.javaClass.canonicalName}")

// ✅ Good: PII in secureLog only
secureLog.info("Oppdrag details", mapOf(
    "personident" to oppdrag.personident,
    "sakId" to oppdrag.sakId,
    "beløp" to oppdrag.beløp
))

// ❌ Bad: PII in appLog
appLog.info("Processing oppdrag for person ${oppdrag.personident}")
appLog.info("SakId ${oppdrag.sakId} has amount ${oppdrag.beløp}")
```

### Null Handling

Handle nulls explicitly with early returns or elvis operator.

```kotlin
// ✅ Good: Clear null handling with early return
val oppdrag = oppdragMapper.readValue(value) ?: run {
    appLog.warn("Failed to parse oppdrag for key $key")
    return stopTimer(key)
}

// ✅ Good: Elvis operator with logging
val kvittering = oppdrag.mmel ?: run {
    appLog.info("No kvittering yet for oppdrag $key")
    return addTimer(key, oppdrag)
}

// ❌ Bad: Silent failure
val oppdrag = oppdragMapper.readValue(value) ?: return
```

---

## 5. Testing Patterns

### Test Naming

Use backtick-quoted descriptive sentences. Mix Norwegian and English naturally. Be specific about the scenario being tested.

```kotlin
// ✅ Good: Descriptive test name with Norwegian domain term
@Test
fun `start iverksetting av vedtak uten utbetaling`() = 
    runTest(TestRuntime.context) {
        val request = TestData.iverksetting(harUtbetaling = false)
        val response = client.post("/iverksetting") { setBody(request) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

// ✅ Good: Clear scenario description
@Test
fun `returns aggregated alert with multiple fagsystems`() {
    val grouped = mapOf(
        "AAP" to listOf(brann1, brann2),
        "TILTPENG" to listOf(brann3)
    )
    val json = jsonAggregated(grouped, config)
    assertTrue(json.contains("3 missing kvitteringer"))
}

// ❌ Bad: Generic, unclear test name
@Test
fun testAlert() { ... }

// ❌ Bad: CamelCase, not descriptive
@Test
fun testMultipleFagsystems() { ... }
```

### Test Structure

Use `TestRuntime` for infrastructure. Use `TestData` factories with sensible defaults. Use `kotlin.test` assertions.

```kotlin
// ✅ Good: Uses TestRuntime and TestData
@Test
fun `creates oppdrag with correct fagområde`() = runTest(TestRuntime.context) {
    val utbetaling = TestData.utbetaling(
        stønadstype = Stønadstype.AAP,
        beløp = 1000
    )
    
    val oppdrag = OppdragService.createOppdrag(utbetaling)
    
    assertEquals("AAP", oppdrag.oppdrag110.kodeFagomraade)
    assertEquals(1000, oppdrag.oppdragsLinje150.first().sats)
}

// ✅ Good: kotlin.test assertions
assertEquals(HttpStatusCode.OK, response.status)
assertNotNull(response.body<Iverksetting>())
assertTrue(branner.isNotEmpty())

// ❌ Bad: Manual assertions
if (response.status != HttpStatusCode.OK) {
    throw AssertionError("Expected OK")
}
```

---

## 6. Code Organization

### Package Structure

Use single-word lowercase package names matching the module name. Avoid nested packages within apps.

```kotlin
// ✅ Good: Simple package matching module
package branntaarn

package utsjekk

package abetal

// ❌ Bad: Nested packages in apps
package branntaarn.clients

package utsjekk.iverksetting.routes
```

### File Size

Target 50-300 lines per file. Split by responsibility when larger. Group related functions in the same file.

**Patterns:**
- Small apps like branntaarn: 30-100 lines per file
- Service classes: 100-200 lines
- Kafka topologies: 150-300 lines
- Data access objects: 50-150 lines

### Dependency Injection

Use constructor injection with default parameters for easy testing. No DI framework. Wire dependencies in app entry point.

```kotlin
// ✅ Good: Clear dependencies, testable with defaults
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

// ✅ Good: App wiring in entry point
fun Application.utsjekk() {
    val config = Config()
    val datasource = Jdbc.initialize(config.jdbc)
    val oppdragProducer = OppdragProducer(config.kafka)
    val iverksettingService = IverksettingService(datasource, oppdragProducer)
    
    routing {
        iverksettingRoutes(iverksettingService)
    }
}

// ❌ Bad: Hidden dependencies, hard to test
class SlackClient {
    private val config = Config()  // Hard to test
    private val client = HttpClient(CIO)  // Can't mock
    
    fun postAggregated(grouped: Map<String, List<Brann>>) { ... }
}
```

---

## 7. Kotlin-Specific Patterns

### Data Classes

Use data classes for DTOs, domain models, and configuration. Leverage destructuring where appropriate.

```kotlin
// ✅ Good: Data classes for structured data
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

// ✅ Good: Destructuring
val (key, timeout, sakId, fagsystem) = brann
```

### Expression Bodies

Use expression bodies for simple functions. Keep multi-line expressions properly formatted.

```kotlin
// ✅ Good: Simple expression body
private fun emoji(config: Config): String = when (config.nais.cluster) {
    "prod-gcp" -> "🔥"
    else -> ""
}

// ✅ Good: Multi-line expression with proper formatting
fun shouldSkipProcessing(now: LocalDateTime): Boolean = 
    now.toLocalDate().erHelligdag() 
        || now.hour < 6 
        || now.hour > 21
```

### Collection Operations

Chain functionally with `.filter { }`, `.map { }`, `.groupBy { }`. Use `buildList` and `buildMap` for construction.

```kotlin
// ✅ Good: Functional chaining
val grouped = branner
    .filter { brann -> brann.timeout.isBefore(now) }
    .groupBy { it.fagsystem }

// ✅ Good: buildList for construction
val blocks = buildList {
    add("""{"type": "header"}""")
    add("""{"type": "section"}""")
    grouped.entries.sortedBy { it.key }.forEach { (fagsystem, branner) ->
        add("""{"type": "divider"}""")
        add("""{"type": "section", "text": "$fagsystem - ${branner.size}"}""")
    }
}

// ❌ Bad: Imperative style with mutation
val grouped = mutableMapOf<String, MutableList<Brann>>()
for (brann in branner) {
    if (brann.timeout.isBefore(now)) {
        if (!grouped.containsKey(brann.fagsystem)) {
            grouped[brann.fagsystem] = mutableListOf()
        }
        grouped[brann.fagsystem]!!.add(brann)
    }
}
```

### String Formatting

Use string templates for interpolation. Use triple-quoted strings with `.trimIndent()` for multiline text, JSON, or XML.

```kotlin
// ✅ Good: String templates
val message = "*$totalCount missing kvitteringer across $fagsystemCount fagsystems*"
appLog.info("Processing oppdrag with key $key")

// ✅ Good: Triple-quoted string with trimIndent
val json = """
{
  "type": "header",
  "text": {
    "type": "plain_text",
    "text": "🔥 Branntårn Alert (${config.nais.cluster})",
    "emoji": true
  }
}
""".trimIndent()

// ❌ Bad: Manual concatenation
val message = "*" + totalCount + " missing kvitteringer across " + fagsystemCount + " fagsystems*"
```

---

## 8. Key Principles

Follow these core tenets when writing code in this project:

1. **Clarity over cleverness** - Code should be obvious to readers. If you need to explain it, simplify it.

2. **Consistency** - Follow established patterns in the codebase. Look for similar code and match its style.

3. **Norwegian domain terms** - Use them confidently for business concepts (iverksetting, avstemming, kvittering). Use English for technical implementation details.

4. **Type safety** - Leverage Kotlin's type system with value classes (`SakId`, `BehandlingId`), sealed interfaces, and strong typing.

5. **Fail fast** - Use early returns, validate at boundaries, handle nulls explicitly.

6. **Test-friendly design** - Use default parameters, dependency injection, and the TestRuntime pattern.

7. **Logging hygiene** - Keep PII out of `appLog`. Use `secureLog` for sensitive data like personident, sakId with context, or payment amounts.

### When in Doubt

- Look for similar code in the codebase using grep/glob
- Prefer existing patterns over inventing new ones
- Ask for clarification on business terminology
- Keep functions small and focused (10-30 lines)
- Use early returns for clarity
- Write tests with descriptive backtick names

### Example: Applying All Principles

```kotlin
// ✅ Excellent: Applies all principles
package branntaarn

import java.time.LocalDateTime

/**
 * Monitors missing kvitteringer and sends aggregated Slack alerts.
 * Runs hourly during operational hours (06-21, weekdays, excluding holidays).
 */
fun branntaarn(
    config: Config = Config(),
    now: LocalDateTime = LocalDateTime.now(),
) {
    // Fail fast: Skip outside operational hours
    if (now.toLocalDate().erHelligdag() || now.hour < 6 || now.hour > 21) return

    val peisschtappern = PeisschtappernClient(config)
    val slack = SlackClient(config)
    
    // Fetch expired timers
    val branner = peisschtappern.branner()
        .filter { brann -> brann.timeout.isBefore(now) }
    
    // Early return if nothing to alert
    if (branner.isEmpty()) return
    
    // Group by fagsystem for aggregated alert
    val grouped = branner.groupBy { it.fagsystem }
    
    // Send single aggregated Slack message
    slack.postAggregated(grouped)
    
    // Cleanup: Slukk (extinguish) timers
    branner.forEach(peisschtappern::slukk)
}
```

This code demonstrates:
- ✅ Clear function name and purpose
- ✅ Default parameters for testability
- ✅ Norwegian domain term (slukk) with comment
- ✅ Early returns for clarity
- ✅ Functional chaining
- ✅ Small, focused function (~20 lines)
- ✅ Type-safe parameters
- ✅ Descriptive variable names
