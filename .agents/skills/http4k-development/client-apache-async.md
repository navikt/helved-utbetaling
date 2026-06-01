---
license: Apache-2.0
module: http4k-client-apache-async
---

# http4k-client-apache-async Reference

Async-only HTTP client backed by Apache HttpAsyncClient 5.x.

## Construction

```kotlin
val client: AsyncHttpHandler = ApacheAsyncClient()

// With custom async client
val client: AsyncHttpHandler = ApacheAsyncClient(
    client = ApacheAsyncClient.defaultApacheAsyncHttpClient()
)
```

`ApacheAsyncClient()` returns an `AsyncHttpHandler` — async callback-based, not sync.

## Async Usage

```kotlin
val client: AsyncHttpHandler = ApacheAsyncClient()

client(Request(GET, "http://localhost:8080/hello")) { response ->
    println(response.status)
    println(response.bodyString())
}
```

## Auto-start

The client automatically starts if it is in `INACTIVE` state when first invoked.

## Error Mapping

| Exception | http4k Status |
|---|---|
| `ConnectException` | `CONNECTION_REFUSED` |
| `UnknownHostException` | `UNKNOWN_HOST` |
| `SocketTimeoutException` | `CLIENT_TIMEOUT` |
| `SocketException` / `IOException` | `SERVICE_UNAVAILABLE` |

## Gotchas

- **Memory-only body mode**: Unlike the sync `ApacheClient`, the async variant does not support `BodyMode.Stream`.
- **Redirects disabled by default**: The default client does not follow redirects.
- **Cookies ignored by default**: The default client uses a no-op cookie spec.
- **No sync invoke**: This client only supports async callbacks. For sync, use `http4k-client-apache`.
- **Apache 5.x**: For Apache HttpClient 4.x async, use `http4k-client-apache4-async`.
