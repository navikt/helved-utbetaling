---
license: Apache-2.0
module: http4k-server-ratpack
---

# http4k-server-ratpack Reference

Ratpack server backend. HTTP only — no WebSocket or SSE.

## Construction

```kotlin
val server = app.asServer(Ratpack(8000)).start()
```

Default stop mode is `Immediate`. Default port is `8000`.

## Stop Modes

| Mode | Supported |
|---|---|
| `StopMode.Immediate` | Yes (default) |
| `StopMode.Graceful(duration)` | No — throws `UnsupportedStopMode` |

## Gotchas

- **Immediate only**: `Ratpack` throws `UnsupportedStopMode` if you pass `Graceful`. This is enforced in the constructor.
- **HTTP only**: Implements `ServerConfig`, not `PolyServerConfig`. No WebSocket or SSE support.
- **Connect queue size**: The stock config sets `connectQueueSize(1000)` on the Ratpack server config.
- **Port discovery**: Uses `server.bindPort` — port 0 (random port) works correctly.
