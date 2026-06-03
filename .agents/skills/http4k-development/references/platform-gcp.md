---
license: Apache-2.0
module: http4k-platform-gcp
---

# http4k-platform-gcp Reference

GCP platform integration — HTTP transport adapter for Google API client libraries.

## GCP SDK HTTP Transport

```kotlin
// Use http4k HttpHandler as the underlying transport for Google API clients
val transport = GcpSdkHttpTransport(httpHandler)
```

Adapts Google's `HttpTransport` to use http4k's `HttpHandler`, converting between `LowLevelHttpRequest`/`LowLevelHttpResponse` and http4k types.

## Gotchas

- **Google API client library**: This adapts the older Google HTTP Client library (`com.google.api.client.http`), not the newer gRPC-based clients.
- **Testability**: Swap in a fake `HttpHandler` to test Google API interactions without real GCP calls.
