---
license: Apache-2.0
module: http4k-client-apache
---

# http4k-client-apache Reference

Synchronous HTTP client backed by Apache HttpClient 5.x.

## Construction

```kotlin
val client: HttpHandler = ApacheClient()

// With custom Apache client
val client: HttpHandler = ApacheClient(
    client = PreCannedApacheHttpClients.defaultApacheHttpClient(),
    responseBodyMode = BodyMode.Memory,
    requestBodyMode = BodyMode.Memory
)
```

`ApacheClient()` returns an `HttpHandler` — it plugs directly into any http4k filter chain or test.

## Streaming

Both request and response bodies support `BodyMode.Memory` (default) and `BodyMode.Stream`:

```kotlin
val client: HttpHandler = ApacheClient(
    responseBodyMode = BodyMode.Stream,
    requestBodyMode = BodyMode.Stream
)
```

## Pre-canned Clients

```kotlin
PreCannedApacheHttpClients.defaultApacheHttpClient()   // no redirects, no cookies
```

## Error Mapping

| Exception | http4k Status |
|---|---|
| `ConnectException` | `CONNECTION_REFUSED` |
| `UnknownHostException` | `UNKNOWN_HOST` |
| `SocketTimeoutException` | `CLIENT_TIMEOUT` |
| `SocketException` / `IOException` | `SERVICE_UNAVAILABLE` |

## Gotchas

- **Redirects disabled by default**: The default client does not follow redirects. Configure the underlying `CloseableHttpClient` if redirect-following is needed.
- **Cookies ignored by default**: The default client uses a no-op cookie spec.
- **Apache 5.x**: This module uses Apache HttpClient 5.x. For 4.x, use `http4k-client-apache4`.
