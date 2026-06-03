---
license: Apache-2.0
module: http4k-server-jetty
---

# http4k-server-jetty Reference

Jetty 12 server backend. Supports HTTP, WebSocket, SSE, and HTTP/2. Loom variant available.

## Construction

```kotlin
val server = app.asServer(Jetty(8000)).start()

// With explicit stop mode
val server = app.asServer(Jetty(8000, StopMode.Immediate)).start()

// With custom Jetty Server instance
val server = app.asServer(Jetty(8000, myJettyServer)).start()

// With connector builders (e.g. HTTP/2)
val server = app.asServer(Jetty(8000, http2(8443, "/path/to/keystore.jks", "password"))).start()
```

Default stop mode is `Graceful(5 seconds)`. Default port is `8000`.

## Loom (Virtual Threads)

```kotlin
val server = app.asServer(JettyLoom(8000)).start()

// With explicit stop mode
val server = app.asServer(JettyLoom(8000, StopMode.Immediate)).start()
```

`JettyLoom` is a factory function that creates a `Jetty` instance with a `QueuedThreadPool` backed by virtual threads.

## HTTP/2

```kotlin
val server = app.asServer(
    Jetty(8000, http2(8443, "/path/to/keystore.jks", "password"))
).start()
```

The `http2()` connector builder configures ALPN + TLS with the Conscrypt provider. Requires a JKS keystore.

## WebSocket and SSE

Jetty implements `PolyServerConfig` — it supports HTTP, WebSocket, and SSE in a single server:

```kotlin
Jetty(8000).toServer(http, ws, sse).start()
```

## Stop Modes

| Mode | Supported |
|---|---|
| `StopMode.Immediate` | Yes |
| `StopMode.Graceful(duration)` | Yes (default, 5s) |

## Gotchas

- **Default is Graceful**: Unlike most other servers, Jetty defaults to `StopMode.Graceful(5 seconds)`. This means `stop()` blocks until in-flight requests complete or the timeout expires.
- **HTTP/2 requires Conscrypt**: The `http2()` connector builder sets `provider = "Conscrypt"`. Ensure the Conscrypt dependency is on the classpath.
- **Port 0 for random port**: Pass `port = 0` to bind to a random available port, then call `server.port()` to discover the actual port.
