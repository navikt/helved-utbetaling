---
license: Apache-2.0
module: http4k-platform-core
---

# http4k-platform-core Reference

Core platform abstractions for cross-cutting concerns when calling remote services.

## RemoteRequestFailed Exceptions

```kotlin
throw RemoteRequestFailed(SERVICE_UNAVAILABLE, "service down", Uri.of("/api"))
throw NotFound("user not found", Uri.of("/users/123"))
throw ClientTimeout("timed out", Uri.of("/slow-endpoint"))
throw Unauthorized("bad token")
throw Forbidden("no access")
throw GatewayTimeout("upstream down")
```

## Server Filter

```kotlin
// Catch RemoteRequestFailed and convert to appropriate HTTP responses
val app = ServerFilters.HandleRemoteRequestFailed()
    .then(myHandler)

// Custom body rendering
val app = ServerFilters.HandleRemoteRequestFailed { localizedMessage }
    .then(myHandler)
```

Maps: `GATEWAY_TIMEOUT`/`CLIENT_TIMEOUT` → 504, `NOT_FOUND` → 404, everything else → 503.

## Client Filter

```kotlin
// Convert non-successful responses to RemoteRequestFailed exceptions
val client = ClientFilters.HandleRemoteRequestFailed()
    .then(httpClient)

// Custom success check
val client = ClientFilters.HandleRemoteRequestFailed(
    responseWasSuccessful = { status.successful || status == NOT_FOUND },
    responseToMessage = { bodyString() }
).then(httpClient)
```

## Gotchas

- **Exception hierarchy**: All exceptions extend `RemoteRequestFailed(status, message, uri?)`. Specific subclasses (`NotFound`, `Unauthorized`, etc.) exist for common cases.
- **Server filter mapping**: The server filter maps timeout exceptions to 504 (not the original status), keeping gateway semantics correct.
