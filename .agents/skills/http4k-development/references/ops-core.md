---
license: Apache-2.0
module: http4k-ops-core
---

# http4k-ops-core Reference

Core metrics abstractions shared by Micrometer and OpenTelemetry integrations.

## MetricsDefaults

```kotlin
data class MetricsDefaults(
    val counterDescription: Pair<String, String>,   // name to description
    val timerDescription: Pair<String, String>,      // name to description
    val labeler: HttpTransactionLabeler              // extracts labels from transactions
)
```

## Pre-built Defaults

```kotlin
// Server metrics: method, status, path labels
MetricsDefaults.server
// Counter: "http.server.request.count"
// Timer:   "http.server.request.latency"

// Client metrics: method, status, host, path labels
MetricsDefaults.client
// Counter: "http.client.request.count"
// Timer:   "http.client.request.latency"
```

## Custom Labels

```kotlin
val custom = MetricsDefaults(
    "my.counter" to "My counter description",
    "my.timer" to "My timer description"
) { transaction ->
    transaction.copy(labels = mapOf(
        "method" to transaction.request.method.toString(),
        "status" to transaction.response.status.code.toString()
    ))
}
```

## Gotchas

- **Foundation module**: This module defines abstractions only. Use `http4k-ops-micrometer` or `http4k-ops-opentelemetry` for actual metrics collection.
- **Label sanitization**: Default labelers sanitize path segments (replace `/`, `.`, regex patterns with `_`).
