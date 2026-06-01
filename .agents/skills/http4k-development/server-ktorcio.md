---
license: Apache-2.0
module: http4k-server-ktorcio
---

# http4k-server-ktorcio Reference

Ktor CIO (Coroutine I/O) server backend. HTTP only — no WebSocket or SSE.

## Construction

```kotlin
val server = app.asServer(KtorCIO(8000)).start()
```

Default stop mode is `Immediate`. Default port is `8000`.

## Stop Modes

| Mode | Supported |
|---|---|
| `StopMode.Immediate` | Yes (default) |
| `StopMode.Graceful(duration)` | No — throws `UnsupportedStopMode` |

## Gotchas

- **Immediate only**: `KtorCIO` throws `UnsupportedStopMode` if you pass any stop mode other than `Immediate`. This is enforced in the constructor.
- **HTTP only**: Implements `ServerConfig`, not `PolyServerConfig`. No WebSocket or SSE support.
- **Null request handling**: If the Ktor request cannot be converted to an http4k `Request`, the handler returns `501 Not Implemented`.
- **Port discovery**: `port()` reads from the Ktor engine config connectors, not from the actual bound socket. Port 0 (random port) may not report correctly.
