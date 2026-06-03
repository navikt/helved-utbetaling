---
license: Apache-2.0
module: http4k-bridge-ktor
---

# http4k-bridge-ktor Reference

Bridge http4k handlers into Ktor as an application plugin.

## Ktor Plugin

```kotlin
fun Application.module() {
    install(KtorToHttp4kApplicationPlugin(myApp))

    // Ktor-defined routes take priority
    routing {
        get("/ktor-route") { call.respondText("from ktor") }
    }
}
```

## How It Works

- Hooks into Ktor's `ResponseBodyReadyForSend` phase
- Only handles requests that Ktor's routing returned `NotFound` for
- Converts between Ktor's `ApplicationRequest`/`ApplicationResponse` and http4k types

## Gotchas

- **Fallback only**: Ktor-defined routes take priority. The http4k handler only receives unmatched requests.
- **Async conversion**: Ktor's async request body (`receiveChannel()`) is converted to a blocking `InputStream`.
- **Streaming responses**: Uses `respondOutputStream()` with flush-on-write for streaming support.
- **Unsafe headers filtered**: Ktor-managed headers (like `Content-Type`) are handled separately to avoid conflicts.
