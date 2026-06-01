---
license: Apache-2.0
module: http4k-server-apache4
---

# http4k-server-apache4 Reference

Apache HttpComponents 4.x (legacy) server backend. HTTP only — no WebSocket or SSE.

## Construction

```kotlin
val server = app.asServer(Apache4Server(8000)).start()
```

Default stop mode is `Immediate`. Default port is `8000`.

## Stop Modes

| Mode | Supported |
|---|---|
| `StopMode.Immediate` | Yes (default) |
| `StopMode.Graceful(duration)` | No — throws `UnsupportedStopMode` |

## Gotchas

- **Immediate only**: `Apache4Server` throws `UnsupportedStopMode` if you pass any stop mode other than `Immediate`. This is enforced in the constructor.
- **HTTP only**: Implements `ServerConfig`, not `PolyServerConfig`. No WebSocket or SSE support.
- **Legacy**: This uses Apache HttpComponents 4.x. Prefer `http4k-server-apache` (5.x) for new projects.
- **Class name**: The class is `Apache4Server`, not `Apache4`.
