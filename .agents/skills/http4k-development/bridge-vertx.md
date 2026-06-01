---
license: Apache-2.0
module: http4k-bridge-vertx
---

# http4k-bridge-vertx Reference

Bridge http4k handlers into Vert.x.

## Vert.x Handler

```kotlin
val handler: (RoutingContext) -> Unit = VertxToHttp4kHandler(myApp)
```

## Router Integration

```kotlin
// Add as fallback to a Vert.x router
router.fallbackToHttp4k(myApp)

// Which is equivalent to:
router.route("/*").blockingHandler(VertxToHttp4kHandler(myApp))
```

## How It Works

- Returns a `(RoutingContext) -> Unit` function
- Converts Vert.x `HttpServerRequest` to http4k `Request` via `Future`-based async
- Writes http4k `Response` back via `HttpServerResponse`

## Gotchas

- **Future-based**: Request conversion returns `Future<Request>` — Vert.x's async pattern. The handler chains via `Future.map()`.
- **Blocking handler**: `fallbackToHttp4k` uses `blockingHandler()` since http4k handlers are synchronous.
- **Buffer response**: Response bodies are sent as Vert.x `Buffer` objects via `response.end(buffer)`.
