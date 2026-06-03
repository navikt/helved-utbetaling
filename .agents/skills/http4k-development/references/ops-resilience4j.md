---
license: Apache-2.0
module: http4k-ops-resilience4j
---

# http4k-ops-resilience4j Reference

Resilience4j integration providing circuit breaker, retry, rate limiter, bulkhead, and time limiter as http4k Filters.

## Circuit Breaker

```kotlin
val cb = CircuitBreaker.ofDefaults("myService")
val app = ResilienceFilters.CircuitBreak(cb).then(myHandler)

// Custom error detection and fallback
val app = ResilienceFilters.CircuitBreak(
    cb = cb,
    isError = { it.status.serverError },                          // what counts as failure
    onError = { Response(SERVICE_UNAVAILABLE).body("fallback") }  // response when circuit open
).then(myHandler)
```

## Retry

```kotlin
val retry = Retry.ofDefaults("myRetry")
val app = ResilienceFilters.RetryFailures(retry).then(myHandler)

// Custom error detection
val app = ResilienceFilters.RetryFailures(
    retry = retry,
    isError = { it.status.serverError }  // retry on server errors
).then(myHandler)
```

## Rate Limiter

```kotlin
val config = RateLimiterConfig.custom()
    .limitRefreshPeriod(Duration.ofSeconds(1))
    .limitForPeriod(10)
    .timeoutDuration(Duration.ofMillis(100))
    .build()
val limiter = RateLimiter.of("myLimiter", config)

val app = ResilienceFilters.RateLimit(limiter).then(myHandler)
// Returns 429 TOO_MANY_REQUESTS when rate exceeded
```

## Bulkhead

```kotlin
val config = BulkheadConfig.custom()
    .maxConcurrentCalls(5)
    .maxWaitDuration(Duration.ZERO)
    .build()
val bulkhead = Bulkhead.of("myBulkhead", config)

val app = ResilienceFilters.Bulkheading(bulkhead).then(myHandler)
// Returns 429 TOO_MANY_REQUESTS when bulkhead full
```

## Time Limiter

```kotlin
val limiter = TimeLimiter.of(Duration.ofSeconds(5))

val app = ResilienceFilters.TimeLimit(limiter).then(myHandler)
// Returns 408 CLIENT_TIMEOUT when time exceeded
```

## Composition

```kotlin
// Chain multiple resilience patterns
val app = ResilienceFilters.CircuitBreak(cb)
    .then(ResilienceFilters.RetryFailures(retry))
    .then(ResilienceFilters.RateLimit(limiter))
    .then(myHandler)
```

## Gotchas

- **All are Filters**: Each resilience pattern is a standard http4k `Filter` — compose with `.then()`.
- **Default error detection**: `isError` defaults to `{ it.status.serverError }` for CircuitBreak and RetryFailures.
- **Default fallback responses**: Circuit open → `503`, rate exceeded → `429`, timeout → `408`, bulkhead full → `429`.
- **Retry consumes response**: RetryFailures catches server errors and retries. The last response (error or success) is returned.
