---
license: Apache-2.0
module: http4k-client-fuel
---

# http4k-client-fuel Reference

Dual sync/async HTTP client backed by the Fuel library.

## Construction

```kotlin
val client: DualSyncAsyncHttpHandler = Fuel()

// With configuration
val client: DualSyncAsyncHttpHandler = Fuel(
    bodyMode = BodyMode.Memory,
    timeout = Duration.ofSeconds(15)
)
```

`Fuel` is a class (not an object factory) that implements `DualSyncAsyncHttpHandler` — it works as both `HttpHandler` and `AsyncHttpHandler`.

## Sync Usage

```kotlin
val client = Fuel()
val response: Response = client(Request(GET, "http://localhost:8080/hello"))
```

## Async Usage

```kotlin
val client = Fuel()
client(Request(GET, "http://localhost:8080/hello")) { response ->
    println(response.status)
}
```

## Streaming

Supports `BodyMode.Memory` (default) and `BodyMode.Stream`:

```kotlin
val client = Fuel(bodyMode = BodyMode.Stream)
```

## Error Mapping

| Exception | http4k Status |
|---|---|
| `ConnectException` | `CONNECTION_REFUSED` |
| `UnknownHostException` | `UNKNOWN_HOST` |
| `SocketTimeoutException` | `CLIENT_TIMEOUT` |
| `SocketException` / `IOException` | `SERVICE_UNAVAILABLE` |

## Gotchas

- **Constructor is a class, not an object**: Unlike most http4k clients, `Fuel` is instantiated as `Fuel()` not `Fuel()` via `invoke`. This is a regular class constructor.
- **Timeout is per-client**: The timeout (default 15 seconds) is set at construction, not per-request.
- **Redirects disabled by default**: Fuel client does not follow redirects.
