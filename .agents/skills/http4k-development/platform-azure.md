---
license: Apache-2.0
module: http4k-platform-azure
---

# http4k-platform-azure Reference

Azure platform integration — HTTP client adapter for Azure SDK.

## Azure HTTP Client Adapter

```kotlin
// Use http4k HttpHandler as the underlying transport for Azure SDK
val azureClient = AzureHttpClient(httpHandler)
```

Converts between Azure SDK's reactive `HttpRequest`/`HttpResponse` and http4k's `Request`/`Response`.

## Gotchas

- **Reactive bridge**: Azure SDK uses Reactor `Mono`/`Flux` types. The adapter converts http4k's synchronous model to reactive streams.
- **Body streaming**: Request bodies are converted from `Flux<ByteBuffer>` to `InputStream` via `PipedInputStream`/`PipedOutputStream`.
- **Testability**: Swap in a fake `HttpHandler` to test Azure SDK interactions without real Azure calls.
