---
license: Apache-2.0
module: http4k-server-websocket
---

# http4k-server-websocket Reference

Standalone Java-WebSocket server backend. WebSocket ONLY — no HTTP or SSE.

## Construction

```kotlin
val wsServer = JavaWebSocket(8000).toWsServer(wsHandler).start()

// With all options
val wsServer = JavaWebSocket(
    port = 8000,
    hostName = "0.0.0.0",
    stopMode = StopMode.Graceful(Duration.ofSeconds(5)),
    addShutdownHook = true,
    startupTimeout = Duration.ofSeconds(5)
).toWsServer(wsHandler).start()
```

Default stop mode is `Immediate`. Default port is `8000`. Default host is `0.0.0.0`.

## Usage

This server is for **WebSocket-only** applications. Use `toWsServer()` instead of `asServer()`:

```kotlin
val ws: WsHandler = { ws ->
    ws.onMessage { msg ->
        ws.send(WsMessage("echo: ${msg.bodyString()}"))
    }
    ws.onClose { println("closed") }
}

val server = JavaWebSocket(8000).toWsServer(ws).start()
```

## Stop Modes

| Mode | Supported |
|---|---|
| `StopMode.Immediate` | Yes (default) |
| `StopMode.Graceful(duration)` | Yes |

## Gotchas

- **WebSocket only**: Passing an `HttpHandler` throws `UnsupportedOperationException("JavaWebSocket does not support http")`. Passing an `SseHandler` throws similarly. Use `toWsServer()`, not `toServer(http, ws, sse)`.
- **WsHandler required**: Passing `null` for the `WsHandler` throws `IllegalStateException("JavaWebSocket requires a WsHandler")`.
- **Shutdown hook**: By default, `addShutdownHook = true` registers a JVM shutdown hook that calls `stop()`. Set to `false` if managing lifecycle externally.
- **Startup timeout**: The server waits up to `startupTimeout` (default 5s) for the underlying WebSocket server to start. Throws if the timeout expires.
- **SO_REUSEADDR**: Enabled by default via the `configFn` parameter. Override `configFn` to change socket options.
- **Binary and text messages**: Both `WsMessage.Mode.Binary` and `WsMessage.Mode.Text` are supported. The mode determines whether `send(ByteBuffer)` or `send(String)` is called.
