---
license: Apache-2.0
module: http4k-client-jetty
---

# http4k-client-jetty Reference

Dual sync/async HTTP client backed by Jetty HttpClient. Also provides a WebSocket client.

## Construction

```kotlin
val client: DualSyncAsyncHttpHandler = JettyClient()

// With custom Jetty HttpClient and request modifier
val client: DualSyncAsyncHttpHandler = JettyClient(
    client = PreCannedJettyHttpClients.defaultJettyHttpClient(),
    bodyMode = BodyMode.Memory,
    requestModifier = { it }
)
```

`JettyClient()` returns a `DualSyncAsyncHttpHandler` — it works as both `HttpHandler` and `AsyncHttpHandler`.

## Sync Usage

```kotlin
val client = JettyClient()
val response: Response = client(Request(GET, "http://localhost:8080/hello"))
```

## Async Usage

```kotlin
val client = JettyClient()
client(Request(GET, "http://localhost:8080/hello")) { response ->
    println(response.status)
}
```

## Request Modifier

The `requestModifier` parameter allows customising every outgoing Jetty request:

```kotlin
val client = JettyClient(
    requestModifier = { req -> req.header("X-Custom", "value") }
)
```

## Streaming

Both request and response bodies support `BodyMode.Memory` (default) and `BodyMode.Stream`:

```kotlin
val client = JettyClient(bodyMode = BodyMode.Stream)
```

## Pre-canned Clients

```kotlin
PreCannedJettyHttpClients.defaultJettyHttpClient()  // no redirects, empty cookie store
```

## Auto-start

The client automatically starts the underlying Jetty `HttpClient` if it is not running when first invoked.

## WebSocket Support

The Jetty module includes `JettyWebsocketClient`:

```kotlin
val ws = JettyWebsocketClient(timeout = Duration.ofSeconds(5))

// Non-blocking
val websocket: Websocket = ws.nonBlocking(
    uri = Uri.of("ws://localhost:8080/ws"),
    headers = Headers.EMPTY,
    onError = { throwable -> },
    onConnect = { websocket -> }
)

// Blocking
val wsClient: WsClient = ws.blocking(
    uri = Uri.of("ws://localhost:8080/ws"),
    headers = Headers.EMPTY
)
```

## Error Mapping

| Exception | http4k Status |
|---|---|
| `ConnectException` | `CONNECTION_REFUSED` |
| `UnknownHostException` | `UNKNOWN_HOST` |
| `SocketTimeoutException` | `CLIENT_TIMEOUT` |
| `SocketException` / `IOException` | `SERVICE_UNAVAILABLE` |

## Gotchas

- **Redirects disabled by default**: The default client does not follow redirects.
- **Cookies cleared by default**: The default client uses an empty cookie store.
- **WebSocket is a separate class**: `JettyWebsocketClient` is independent from `JettyClient` — construct it separately. It uses its own `WebSocketClient` backed by a Jetty `HttpClient`.
