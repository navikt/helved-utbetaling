---
license: Apache-2.0
module: http4k-client-websocket
---

# http4k-client-websocket Reference

Standalone WebSocket client backed by Java-WebSocket (TooTallNate). Use this when you need WebSocket support without an HTTP client dependency.

## Construction

```kotlin
val ws: WebsocketFactory = WebsocketClient()

// With configuration
val ws: WebsocketFactory = WebsocketClient(
    timeout = Duration.ofSeconds(5),
    autoReconnection = false,
    draft = Draft_6455()
)
```

`WebsocketClient()` returns a `WebsocketFactory` — it creates WebSocket connections, not HTTP requests.

## Non-blocking Usage

```kotlin
val ws = WebsocketClient()

val websocket: Websocket = ws.nonBlocking(
    uri = Uri.of("ws://localhost:8080/ws"),
    headers = Headers.EMPTY,
    onError = { throwable -> println("error: $throwable") },
    onConnect = { websocket ->
        websocket.onMessage { println(it.bodyString()) }
        websocket.send(WsMessage("hello"))
    }
)
```

## Blocking Usage

```kotlin
val ws = WebsocketClient()

val wsClient: WsClient = ws.blocking(
    uri = Uri.of("ws://localhost:8080/ws"),
    headers = Headers.EMPTY
)

wsClient.send(WsMessage("hello"))
val received: WsMessage = wsClient.received().first()
```

## Configuration

| Parameter | Default | Description |
|---|---|---|
| `timeout` | 5 seconds | Connection timeout |
| `autoReconnection` | `false` | Automatically reconnect on disconnect |
| `draft` | `Draft_6455()` | WebSocket protocol draft (RFC 6455) |

## Gotchas

- **No HTTP support**: This module is WebSocket-only. It does not provide an `HttpHandler`. For combined HTTP + WebSocket, use `http4k-client-jetty`, `http4k-client-okhttp`, or `http4k-client-helidon`.
- **Java-WebSocket library**: Backed by the `org.java-websocket` library, not a full HTTP client engine.
- **Auto-reconnection is off by default**: Set `autoReconnection = true` if you need automatic reconnection on disconnect.
