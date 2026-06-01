---
license: Apache-2.0
module: http4k-bridge-ratpack
---

# http4k-bridge-ratpack Reference

Bridge http4k handlers into Ratpack.

## Ratpack Handler

```kotlin
val handler = RatpackToHttp4kHandler(myApp)
```

## Router Integration

```kotlin
// Add as fallback handler to a Ratpack router
router.fallbackToHttp4k(myApp)
```

## How It Works

- Wraps the http4k handler as a Ratpack `Handler`
- Waits for the request body via `context.request.body.then()`
- Converts between Ratpack's `Context`/`TypedData` and http4k types

## Gotchas

- **Async body**: Ratpack request bodies are async — the adapter uses `.then()` to wait for the body before invoking the http4k handler.
- **Method validation**: Unsupported HTTP methods return `501 NOT_IMPLEMENTED`.
- **RequestSource**: Extracts `remoteAddress.host` and `remoteAddress.port` from the Ratpack request.
- **Byte array response**: Response bodies are sent as byte arrays via `context.response.send()`.
