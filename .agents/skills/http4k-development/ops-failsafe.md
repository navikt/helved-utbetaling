---
license: Apache-2.0
module: http4k-ops-failsafe
---

# http4k-ops-failsafe Reference

Failsafe integration providing a single unified filter for circuit breaker, retry, rate limiter, bulkhead, timeout, and fallback policies.

## Basic Usage

```kotlin
val executor = Failsafe.with(
    CircuitBreaker.builder<Response>()
        .withFailureThreshold(3)
        .handleResultIf { !it.status.successful }
        .build()
)

val app = FailsafeFilter(executor).then(myHandler)
```

## Multiple Policies

```kotlin
val executor = Failsafe.with(
    Fallback.of(Response(OK).body("fallback")),   // outermost: fallback
    CircuitBreaker.builder<Response>()
        .withFailureThreshold(3)
        .handleResultIf { !it.status.successful }
        .build(),
    Retry.builder<Response>()
        .withMaxRetries(2)
        .handleResultIf { !it.status.successful }
        .build()
)

val app = FailsafeFilter(executor).then(myHandler)
```

## Individual Policies

```kotlin
// Circuit breaker
FailsafeFilter(Failsafe.with(
    CircuitBreaker.builder<Response>().withFailureThreshold(1).handleResultIf { !it.status.successful }.build()
))
// Returns 503 "Circuit is open" when open

// Bulkhead
FailsafeFilter(Failsafe.with(Bulkhead.of<Response>(5)))
// Returns 429 "Bulkhead limit exceeded" when full

// Timeout
FailsafeFilter(Failsafe.with(Timeout.of<Response>(Duration.ofSeconds(5))))
// Returns 408 CLIENT_TIMEOUT when exceeded

// Rate limiter
FailsafeFilter(Failsafe.with(RateLimiter.smoothBuilder<Response>(10, Duration.ofSeconds(1)).build()))
// Returns 429 "Rate limit exceeded" when exceeded
```

## Custom Error Handler

```kotlin
val app = FailsafeFilter(executor) { error ->
    when (error) {
        is CircuitBreakerOpenException -> Response(SERVICE_UNAVAILABLE).body("service down")
        is TimeoutExceededException -> Response(GATEWAY_TIMEOUT).body("upstream timeout")
        else -> Response(INTERNAL_SERVER_ERROR)
    }
}.then(myHandler)
```

## Gotchas

- **Single filter**: Unlike Resilience4j (separate filters per pattern), Failsafe uses one `FailsafeFilter` wrapping a `FailsafeExecutor` with all configured policies.
- **Policy order matters**: Failsafe applies policies in reverse order — the first policy in `Failsafe.with()` is the outermost wrapper (e.g., Fallback should be first).
- **Default error mapping**: `CircuitBreakerOpenException` → 503, `BulkheadFullException` → 429, `TimeoutExceededException` → 408, `RateLimitExceededException` → 429.
- **Fallback policy**: Use `Fallback.of(response)` to provide a default response when all retries/circuit breakers fail.
