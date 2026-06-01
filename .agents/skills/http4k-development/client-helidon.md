---
license: Apache-2.0
module: http4k-client-helidon
---

# http4k-client-helidon Reference

Synchronous HTTP client backed by Helidon WebClient. Also provides a WebSocket client.

## Construction

```kotlin
val client: HttpHandler = HelidonClient()

// With custom Helidon WebClient and request modifier
val client: HttpHandler = HelidonClient(
    client = WebClient.builder().followRedirects(false).build(),
    bodyMode = BodyMode.Memory,
    requestModifier = { it }
)
```

`HelidonClient()` returns an `HttpHandler` — it plugs directly into any http4k filter chain or test.

## Request Modifier

The `requestModifier` parameter allows customising every outgoing Helidon request:

```kotlin
val client: HttpHandler = HelidonClient(
    requestModifier = { req -> req.header(HeaderNames.create("X-Custom"), "value") }
)
```

## WebSocket Support

The Helidon module includes `HelidonWebsocketClient`:

```kotlin
val ws = HelidonWebsocketClient(timeout = Duration.ofSeconds(5))

// Non-blocking
val websocket: Websocket = ws.nonBlocking(
    uri = Uri.of("ws://localhost:8080/ws"),
    headers = Headers.EMPTY,
    onError = { throwable -> },
    onConnect = { websocket -> }
)

// Blocking
val wsClient: Http4kWsClient = ws.blocking(
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

- **Sync only**: `HelidonClient` is sync-only (`HttpHandler`). There is no async variant.
- **Redirects disabled by default**: The default `WebClient` is built with `followRedirects(false)`.
- **WebSocket is a separate class**: `HelidonWebsocketClient` is independent from `HelidonClient` — they do not share a connection pool.
