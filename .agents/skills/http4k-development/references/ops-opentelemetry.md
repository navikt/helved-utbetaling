---
license: Apache-2.0
module: http4k-ops-opentelemetry
---

# http4k-ops-opentelemetry Reference

OpenTelemetry tracing and metrics integration. Distributed tracing with context propagation and semantic convention metrics.

## Server Tracing

```kotlin
val app = ServerFilters.OpenTelemetryTracing()
    .then(myHandler)

// With custom configuration
val app = ServerFilters.OpenTelemetryTracing(
    openTelemetry = GlobalOpenTelemetry.get(),
    spanNamer = { req -> "${req.method} ${req.uri.path}" },
    spanCreationMutator = { builder, request ->
        builder.setAttribute("custom.attr", request.header("X-Custom") ?: "")
    },
    spanCompletionMutator = { span, request, response ->
        span.setAttribute("http.response.size", response.bodyString().length)
    }
).then(myHandler)
```

## Client Tracing

```kotlin
val client = ClientFilters.OpenTelemetryTracing()
    .then(httpClient)

// With custom configuration
val client = ClientFilters.OpenTelemetryTracing(
    openTelemetry = GlobalOpenTelemetry.get(),
    spanNamer = { req -> "HTTP ${req.method}" },
    spanCreationMutator = { builder -> builder.setAttribute("peer.service", "downstream") },
    spanCompletionMutator = { span, _, response ->
        span.setAttribute("response.cached", response.header("X-Cache") ?: "MISS")
    }
).then(httpClient)
```

## Attribute Key Conventions

Tracing filters accept an `attributeKeys` parameter to control span attribute names:

```kotlin
// OpenTelemetryAttributesKeys interface defines the attribute key contract
// Two built-in implementations:

// OTel Semantic Conventions (recommended for new projects)
val app = ServerFilters.OpenTelemetryTracing(
    attributeKeys = OpenTelemetrySemanticConventions
).then(myHandler)
// Uses: http.request.method, url.full, user_agent.original, client.address,
//       http.route, http.response.status_code
// Note: serverUrl is null (not set on server spans)

// Legacy http4k conventions (deprecated, backward-compatible)
val app = ServerFilters.OpenTelemetryTracing(
    attributeKeys = LegacyHttp4kConventions
).then(myHandler)
// Uses: http.method, http.url, http.user_agent, http.client_ip,
//       http.route, http.status_code

// Works on both server and client filters
val client = ClientFilters.OpenTelemetryTracing(
    attributeKeys = OpenTelemetrySemanticConventions  // this is the default
).then(httpClient)
```

## SSE Tracing

```kotlin
// Basic SSE server tracing — returns SseFilter
val app = ServerFilters.OpenTelemetrySseTracing()
    .then(mySseHandler)

// With custom configuration
val app = ServerFilters.OpenTelemetrySseTracing(
    openTelemetry = GlobalOpenTelemetry.get(),
    spanNamer = { req -> "${req.method} ${req.uri.path}" },
    spanCreationMutator = { builder, request ->
        builder.setAttribute("custom.attr", request.header("X-Custom") ?: "")
    },
    spanCompletionMutator = { span, request, sseResponse ->
        span.setAttribute("sse.status", sseResponse.status.code)
    },
    attributesKeys = OpenTelemetrySemanticConventions
).then(mySseHandler)

// Span lifecycle: span lives for SSE connection lifetime
// spanCompletionMutator fires on close(), then span.end() is called
// (contrast with HTTP tracing where span ends when response is returned)
```

## Poly Tracing

```kotlin
// Trace both HTTP and SSE handlers in a PolyHandler
val poly = PolyFilters.OpenTelemetryTracing()
    .then(myPolyHandler)

// With separate mutators for HTTP and SSE
val poly = PolyFilters.OpenTelemetryTracing(
    openTelemetry = GlobalOpenTelemetry.get(),
    spanNamer = { req -> "${req.method} ${req.uri.path}" },
    spanCreationMutator = { builder, request -> builder },
    httpSpanCompletionMutator = { span, request, response ->
        span.setAttribute("http.done", true)
    },
    sseSpanCompletionMutator = { span, request, sseResponse ->
        span.setAttribute("sse.done", true)
    }
).then(myPolyHandler)
```

## Server Metrics

```kotlin
// Standard metrics
val app = ServerFilters.OpenTelemetryMetrics.RequestTimer()
    .then(ServerFilters.OpenTelemetryMetrics.RequestCounter())
    .then(myHandler)

// OTel 2.x semantic conventions (recommended)
val app = ServerFilters.OpenTelemetry2xMetrics.RequestDuration()
    .then(myHandler)
// Records: http.server.request.duration (histogram in seconds)
// Labels: http.request.method, http.response.status_code, http.route, url.scheme
```

## Client Metrics

```kotlin
val client = ClientFilters.OpenTelemetryMetrics.RequestTimer()
    .then(ClientFilters.OpenTelemetryMetrics.RequestCounter())
    .then(httpClient)

// OTel 2.x semantic conventions
val client = ClientFilters.OpenTelemetry2xMetrics.RequestDuration()
    .then(httpClient)
// Records: http.client.request.duration (histogram in seconds)
// Labels: http.request.method, http.response.status_code, server.address, server.port
```

## Event Tracing

```kotlin
// Add OpenTelemetry trace context to http4k Events
val events = EventFilters.AddOpenTelemetryTraces().then(myEvents)
```

## AutoOpenTelemetryEvents

Bridge http4k Events to OpenTelemetry logs and span events:

```kotlin
val events = AutoOpenTelemetryEvents(Jackson)

data class OrderProcessed(val orderId: String, val amount: Double) : Event

// Without active span → emitted as OTel log record
events(OrderProcessed("order-123", 99.99))

// With active span → emitted as span event
val span = tracer.spanBuilder("checkout").startSpan()
span.makeCurrent().use {
    events(OrderProcessed("order-123", 99.99))  // attached to span as "OrderProcessed" event
}

// With metadata
val event = OrderProcessed("order-123", 99.99) + ("region" to "eu-west")
events(event)  // metadata fields become string attributes
```

Attribute typing:
- String → `StringKey`, Int → `LongKey`, Double → `DoubleKey`, Boolean → `BooleanKey`
- Nested objects use dot-separated keys: `nested.inner`
- Arrays serialized as JSON strings
- Error events (`Event.Error`) get `Severity.ERROR`; others get `Severity.INFO`

## Gotchas

- **GlobalOpenTelemetry by default**: Filters use `GlobalOpenTelemetry.get()` if no explicit instance is passed. Configure the SDK before creating filters.
- **Context propagation automatic**: Client filter injects trace headers; server filter extracts them. W3C TraceContext and B3 propagation work out of the box.
- **Span kind**: Server filter creates `SERVER` spans; client filter creates `CLIENT` spans.
- **Error status**: Server errors (`5xx`) set span status to `ERROR`. Client errors (`4xx`) set `UNSET` on server, `ERROR` on client.
- **Instrumentation name**: All spans/metrics use `"http4k"` as the instrumentation library name.
- **Semantic convention metrics**: `OpenTelemetry2xMetrics` follows OTel semantic conventions v2 (duration in seconds, standard attribute names). Prefer this for new projects.
- **Defaults to OpenTelemetrySemanticConventions**: All tracing filters (`ClientFilters`, `ServerFilters`, `PolyFilters`, `ServerFilters.OpenTelemetrySseTracing`) default to `OpenTelemetrySemanticConventions`. `LegacyHttp4kConventions` is deprecated.
- **defaultSpanNamer includes URI for non-template requests**: For requests without a `UriTemplate` (e.g., client-side calls), the default span name is `"METHOD uri"` (e.g., `"GET /api/users"`). For router-matched requests, it's `"METHOD /template"`. Override `spanNamer` if you want different behaviour.
- **SSE span lifecycle**: Unlike HTTP tracing (span ends when response returns), SSE spans live for the connection lifetime. The span ends when `close()` is called on the SSE connection, not when the `SseResponse` is returned.
- **SSE span context propagation**: Span context is automatically restored inside SSE consumers, so `Span.current()` is valid within the consumer callback.
- **AutoOpenTelemetryEvents requires format-core**: The `http4k-format-core` dependency is needed for JSON type introspection.
