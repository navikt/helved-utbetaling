---
license: Apache-2.0
module: http4k-server-ktornetty
---

# http4k-server-ktornetty Reference

Ktor with Netty engine server backend. HTTP only — no WebSocket or SSE.

## Construction

```kotlin
val server = app.asServer(KtorNetty(8000)).start()

// With graceful shutdown
val server = app.asServer(KtorNetty(8000, StopMode.Graceful(Duration.ofSeconds(10)))).start()
```

Default stop mode is `Immediate`. Default port is `8000`.

## Stop Modes

| Mode | Supported |
|---|---|
| `StopMode.Immediate` | Yes (default) |
| `StopMode.Graceful(duration)` | Yes |

## Gotchas

- **HTTP only**: Implements `ServerConfig`, not `PolyServerConfig`. No WebSocket or SSE support. For WebSocket via Netty, use `http4k-server-netty` instead.
- **Null request handling**: Same as KtorCIO — if the Ktor request cannot be converted, returns `501 Not Implemented`.
- **Port discovery**: `port()` reads from the Ktor engine config connectors, not the actual bound socket.
- **Blocking handlers run on `Dispatchers.IO`**: The handler invocation is wrapped in `withContext(Dispatchers.IO)`, so standard blocking http4k handlers work correctly without coroutine dispatcher issues.
