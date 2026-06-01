---
license: Apache-2.0
module: http4k-connect-google-analytics-core
---

# http4k-connect-google-analytics-core Reference

Shared models and types for Google Analytics integrations (UA and GA4).

## Core Types

```kotlin
// Analytics collector type alias — send events to any analytics backend
typealias AnalyticsCollector = (Analytics) -> Unit

// Sealed type for analytics events
sealed interface Analytics
data class Event(val name: String, val params: Map<String, String> = emptyMap()) : Analytics
data class PageView(val path: String, val title: String? = null) : Analytics
```

## Usage Pattern

```kotlin
// Compose multiple analytics backends
fun multiAnalytics(vararg collectors: AnalyticsCollector): AnalyticsCollector = { event ->
    collectors.forEach { it(event) }
}

val analytics: AnalyticsCollector = multiAnalytics(
    ga4Analytics,
    uaAnalytics
)

// Send events
analytics(Event("button_click", mapOf("button_id" to "signup")))
analytics(PageView("/home", "Home Page"))
```

## Gotchas

- This is a shared models module — no HTTP client included
- Add `connect-google-analytics-ga4` or `connect-google-analytics-ua` for actual implementations
- `AnalyticsCollector` is a type alias for `(Analytics) -> Unit` — easy to implement test doubles
