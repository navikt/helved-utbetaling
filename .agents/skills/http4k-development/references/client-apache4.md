---
license: Apache-2.0
module: http4k-client-apache4
---

# http4k-client-apache4 Reference

Synchronous HTTP client backed by Apache HttpClient 4.x.

## Construction

```kotlin
val client: HttpHandler = Apache4Client()

// With custom Apache 4 client
val client: HttpHandler = Apache4Client(
    client = PreCannedApache4HttpClients.defaultApacheHttpClient(),
    responseBodyMode = BodyMode.Memory,
    requestBodyMode = BodyMode.Memory
)
```

`Apache4Client()` returns an `HttpHandler` — it plugs directly into any http4k filter chain or test.

## Streaming

Both request and response bodies support `BodyMode.Memory` (default) and `BodyMode.Stream`:

```kotlin
val client: HttpHandler = Apache4Client(
    responseBodyMode = BodyMode.Stream,
    requestBodyMode = BodyMode.Stream
)
```

## Pre-canned Clients

```kotlin
PreCannedApache4HttpClients.defaultApacheHttpClient()    // no redirects, no cookies
PreCannedApache4HttpClients.insecureApacheHttpClient()   // additionally trusts all TLS certs
```

## Error Mapping

| Exception | http4k Status |
|---|---|
| `ConnectException` | `CONNECTION_REFUSED` |
| `UnknownHostException` | `UNKNOWN_HOST` |
| `SocketTimeoutException` | `CLIENT_TIMEOUT` |
| `SocketException` / `IOException` | `SERVICE_UNAVAILABLE` |

## Gotchas

- **Apache 4.x**: This module uses the legacy Apache HttpClient 4.x. Prefer `http4k-client-apache` (5.x) for new projects.
- **Redirects disabled by default**: The default client does not follow redirects.
- **Cookies ignored by default**: The default client uses `IGNORE_COOKIES` cookie spec.
