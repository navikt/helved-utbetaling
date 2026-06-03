---
license: Apache-2.0
module: http4k-server-undertow
---

# http4k-server-undertow Reference

Undertow server backend. Supports HTTP, WebSocket, and SSE.

## Construction

```kotlin
val server = app.asServer(Undertow(8000)).start()

// With graceful shutdown
val server = app.asServer(Undertow(8000, StopMode.Graceful(Duration.ofSeconds(10)))).start()
```

Default stop mode is `Immediate`. Default port is `8000`.

## WebSocket and SSE

Undertow implements `PolyServerConfig` — it supports HTTP, WebSocket, and SSE in a single server:

```kotlin
val ws: WsHandler = { ws ->
    ws.onMessage { ws.send(WsMessage("echo: ${it.bodyString()}")) }
    ws.onClose { println("closed") }
}

val sse: SseHandler = { sse ->
    sse.send(SseMessage.Data("hello"))
    sse.close()
}

val http: HttpHandler = { Response(OK).body("hello") }

{ http.asServer(Undertow(8000)).start() }

// Combined server with all protocols
Undertow(8000).toServer(http, ws, sse).start()
```

## Stop Modes

| Mode | Supported |
|---|---|
| `StopMode.Immediate` | Yes (default) |
| `StopMode.Graceful(duration)` | Yes |

## Gotchas

- **Default is Immediate**: Unlike Jetty, Undertow defaults to `StopMode.Immediate`. Pass `StopMode.Graceful(duration)` explicitly for graceful shutdown.
- **No HTTP/2 out of the box**: The stock `Undertow` config does not expose HTTP/2 setup. Duplicate the class and configure the Undertow builder directly if needed.
