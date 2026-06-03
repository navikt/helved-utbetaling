---
license: Apache-2.0
module: http4k-client-okhttp
---

# http4k-client-okhttp Reference

Dual sync/async HTTP client backed by OkHttp. Also provides a WebSocket client.

## Construction

```kotlin
val client: DualSyncAsyncHttpHandler = OkHttp()

// With custom OkHttpClient
val client: DualSyncAsyncHttpHandler = OkHttp(
    client = PreCannedOkHttpClients.defaultOkHttpClient(),
    bodyMode = BodyMode.Memory
)
```

`OkHttp()` returns a `DualSyncAsyncHttpHandler` — it works as both `HttpHandler` and `AsyncHttpHandler`.

## Sync Usage

```kotlin
val client = OkHttp()
val response: Response = client(Request(GET, "http://localhost:8080/hello"))
```

## Async Usage

```kotlin
val client = OkHttp()
client(Request(GET, "http://localhost:8080/hello")) { response ->
    println(response.status)
}
```

## Pre-canned Clients

```kotlin
PreCannedOkHttpClients.defaultOkHttpClient()    // no redirects
PreCannedOkHttpClients.insecureOkHttpClient()   // additionally trusts all TLS certs
```

## WebSocket Support

The OkHttp module includes `OkHttpWebsocketClient`:

```kotlin
val ws = OkHttpWebsocketClient(timeout = Duration.ofSeconds(5))

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

## Streaming Request Bodies

Use `BodyMode.Stream` to stream request bodies without buffering into memory:

```kotlin
val client = OkHttp(bodyMode = BodyMode.Stream)

// Stream a large file upload — body is written directly to OkHttp's sink
val response = client(
    Request(POST, "http://host/upload")
        .header("content-length", fileSize.toString())
        .body(fileInputStream)
)
```

In `BodyMode.Memory` (default), the request body is fully buffered into a byte array before sending.

## Gotchas

- **Redirects disabled by default**: The default `OkHttpClient` is configured with `followRedirects(false)`.
- **WebSocket is a separate class**: `OkHttpWebsocketClient` is independent from `OkHttp` — construct it separately with its own `OkHttpClient`.
- **Stream mode for large uploads**: Use `BodyMode.Stream` for large request payloads to avoid OOM. The content-length header is used as the `RequestBody.contentLength()` hint; omitting it sends with `contentLength() = -1` (chunked).
- **Popular choice**: OkHttp is the most widely-used http4k client backend due to OkHttp's maturity and broad feature set.
