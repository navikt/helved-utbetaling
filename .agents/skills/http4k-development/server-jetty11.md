---
license: Apache-2.0
module: http4k-server-jetty11
---

# http4k-server-jetty11 Reference

Jetty 11 (Jakarta Servlet) server backend. Supports HTTP, WebSocket, SSE, and HTTP/2. Loom variant available.

## Construction

```kotlin
val server = app.asServer(Jetty11(8000)).start()

// With explicit stop mode
val server = app.asServer(Jetty11(8000, StopMode.Immediate)).start()

// With connector builders (e.g. HTTP/2)
val server = app.asServer(Jetty11(8000, http2(8443, "/path/to/keystore.jks", "password"))).start()
```

Default stop mode is `Graceful(5 seconds)`. Default port is `8000`.

## Loom (Virtual Threads)

```kotlin
val server = app.asServer(Jetty11Loom(8000)).start()
val server = app.asServer(Jetty11Loom(8000, StopMode.Immediate)).start()
```

## HTTP/2

```kotlin
val server = app.asServer(
    Jetty11(8000, http2(8443, "/path/to/keystore.jks", "password"))
).start()
```

Same ALPN + Conscrypt setup as the Jetty 12 module.

## WebSocket and SSE

Jetty11 implements `PolyServerConfig`:

```kotlin
Jetty11(8000).toServer(http, ws, sse).start()
```

## Stop Modes

| Mode | Supported |
|---|---|
| `StopMode.Immediate` | Yes |
| `StopMode.Graceful(duration)` | Yes (default, 5s) |

## Gotchas

- **Jetty 11 vs Jetty 12**: This module uses Jetty 11 with `jakarta.servlet`. Use `http4k-server-jetty` for Jetty 12 (non-servlet API). The two are not interchangeable.
- **Handler insertion order**: Jetty11 uses `server.insertHandler()` which prepends handlers. The order is: HTTP handler first, then SSE, then WebSocket.
- **Default is Graceful**: Same as Jetty 12 — defaults to `StopMode.Graceful(5 seconds)`.
