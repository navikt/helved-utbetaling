---
license: Apache-2.0
module: http4k-ops-micrometer
---

# http4k-ops-micrometer Reference

Micrometer metrics integration. Timer and counter filters for server and client HTTP transactions.

## Server Metrics

```kotlin
val registry = SimpleMeterRegistry()  // or PrometheusMeterRegistry, etc.

val app = ServerFilters.MicrometerMetrics.RequestTimer(registry)
    .then(ServerFilters.MicrometerMetrics.RequestCounter(registry))
    .then(myHandler)
```

## Client Metrics

```kotlin
val client = ClientFilters.MicrometerMetrics.RequestTimer(registry)
    .then(ClientFilters.MicrometerMetrics.RequestCounter(registry))
    .then(httpClient)
```

## Custom Configuration

```kotlin
val timer = ServerFilters.MicrometerMetrics.RequestTimer(
    meterRegistry = registry,
    name = "api.requests.duration",
    description = "API request duration",
    labeler = { it.label("endpoint", it.routingGroup) },
    clock = Clock.systemUTC()
)

val counter = ServerFilters.MicrometerMetrics.RequestCounter(
    meterRegistry = registry,
    name = "api.requests.total",
    description = "Total API requests",
    labeler = { it.label("method", it.request.method.toString()) }
)
```

## Default Labels

Server metrics include: `method`, `status`, `path`
Client metrics include: `method`, `status`, `host`, `path`

## Gotchas

- **Histogram by default**: `RequestTimer` uses `publishPercentileHistogram()` for rich latency distribution data.
- **Routing group as path**: The `path` label uses `routingGroup` (the route template like `/users/{id}`) not the actual request path, preventing cardinality explosion.
- **Two separate filters**: Timer and counter are separate filters — apply both if you want both metrics.
