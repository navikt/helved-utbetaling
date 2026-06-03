---
license: Apache-2.0
module: http4k-server-apache
---

# http4k-server-apache Reference

Apache HttpComponents 5.x server backend. HTTP only — no WebSocket or SSE.

## Construction

```kotlin
val server = app.asServer(ApacheServer(8000)).start()

// With explicit stop mode
val server = app.asServer(ApacheServer(8000, StopMode.Immediate)).start()
```

Default stop mode is `Graceful(5 seconds)`. Default port is `8000`.

## Stop Modes

| Mode | Supported |
|---|---|
| `StopMode.Immediate` | Yes |
| `StopMode.Graceful(duration)` | Yes (default, 5s) |

Graceful shutdown calls `initiateShutdown()` then `awaitTermination(timeout)` before closing.

## Gotchas

- **HTTP only**: `ApacheServer` implements `ServerConfig`, not `PolyServerConfig`. It does not support WebSocket or SSE.
- **Class name**: The class is `ApacheServer`, not `Apache`. This avoids collision with the Apache HTTP client module.
- **Default is Graceful**: Defaults to `StopMode.Graceful(5 seconds)`.
