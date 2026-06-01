---
license: Apache-2.0
module: http4k-server-helidon
---

# http4k-server-helidon Reference

Helidon SE 4.x server backend. Supports HTTP, WebSocket, and SSE.

## Construction

```kotlin
val server = app.asServer(Helidon(8000)).start()

// With graceful shutdown
val server = app.asServer(Helidon(8000, StopMode.Graceful(Duration.ofSeconds(10)))).start()
```

Default stop mode is `Immediate`. Default port is `8000`.

## WebSocket and SSE

Helidon implements `PolyServerConfig` — it supports HTTP, WebSocket, and SSE:

```kotlin
Helidon(8000).toServer(http, ws, sse).start()
```

SSE is handled within the HTTP routing via `HelidonToHttp4kHandler`. WebSocket uses a separate `WsRouting` with a wildcard endpoint.

## Stop Modes

| Mode | Supported |
|---|---|
| `StopMode.Immediate` | Yes (default) |
| `StopMode.Graceful(duration)` | Yes |

## Gotchas

- **Graceful shutdown quirks**: Helidon supports graceful shutdown via `shutdownGracePeriod()`, but it has known issues in Docker environments. The integration test for graceful stop is disabled with the note: "Helidon is in a half-broken state with respect to stop modes."
- **Helidon 4.x only**: This bridges to Helidon SE 4.x (virtual-thread-friendly). Not compatible with Helidon 3.x.
- **HTTP/2 native**: Helidon 4.x supports HTTP/2 natively, but the stock http4k config does not expose HTTP/2 settings. Configure the `WebServer.builder()` directly if needed.
