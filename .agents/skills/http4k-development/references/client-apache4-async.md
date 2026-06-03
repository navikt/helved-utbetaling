---
license: Apache-2.0
module: http4k-client-apache4-async
---

# http4k-client-apache4-async Reference

Async-only HTTP client backed by Apache HttpAsyncClient 4.x.

## Construction

```kotlin
val client: AsyncHttpHandler = Apache4AsyncClient()

// With custom async client
val client: AsyncHttpHandler = Apache4AsyncClient(
    client = Apache4AsyncClient.defaultApacheAsyncHttpClient(),
    responseBodyMode = BodyMode.Memory,
    requestBodyMode = BodyMode.Memory
)
```

`Apache4AsyncClient()` returns an `AsyncHttpHandler` — async callback-based, not sync.

## Async Usage

```kotlin
val client: AsyncHttpHandler = Apache4AsyncClient()

client(Request(GET, "http://localhost:8080/hello")) { response ->
    println(response.status)
    println(response.bodyString())
}
```

## Streaming

Both request and response bodies support `BodyMode.Memory` (default) and `BodyMode.Stream`:

```kotlin
val client: AsyncHttpHandler = Apache4AsyncClient(
    responseBodyMode = BodyMode.Stream,
    requestBodyMode = BodyMode.Stream
)
```

## Auto-start

The client automatically starts if it is not running when first invoked.

## Error Mapping

| Exception | http4k Status |
|---|---|
| `ConnectException` | `CONNECTION_REFUSED` |
| `UnknownHostException` | `UNKNOWN_HOST` |
| `SocketTimeoutException` | `CLIENT_TIMEOUT` |
| `SocketException` / `IOException` | `SERVICE_UNAVAILABLE` |

## Gotchas

- **Apache 4.x**: This module uses the legacy Apache HttpAsyncClient 4.x. Prefer `http4k-client-apache-async` (5.x) for new projects.
- **Redirects disabled by default**: The default client does not follow redirects.
- **Cookies ignored by default**: The default client uses `IGNORE_COOKIES` cookie spec.
- **No sync invoke**: This client only supports async callbacks. For sync, use `http4k-client-apache4`.
