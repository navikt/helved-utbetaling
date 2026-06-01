---
license: Apache-2.0
module: http4k-bridge-spring
---

# http4k-bridge-spring Reference

Bridge http4k handlers into Spring MVC as a fallback controller, and expose http4k SSE/WebSocket handlers via Spring's async infrastructure.

## HTTP Fallback Controller

```kotlin
@Controller
class Http4kFallback : SpringToHttp4kFallbackController(myApp)
```

Catches all unmatched routes (`/**`) and delegates to the http4k handler. Spring-mapped routes take priority.

- `@RequestMapping(value = ["**"])` catches all HTTP methods on unmatched paths
- Delegates to the http4k handler via Jakarta servlet adapter
- Automatic `@ExceptionHandler` for `LensFailure` → `400 BAD_REQUEST` with `ProblemDetail`

## SSE Controller

Extend `SpringToHttp4kSseController` to bridge an http4k `SseHandler` into Spring MVC's `SseEmitter` infrastructure:

```kotlin
@Controller
class MySseController : SpringToHttp4kSseController({ req ->
    SseResponse { sse ->
        sse.send(SseMessage.Data("hello"))
        sse.send(SseMessage.Event("greet", "world", SseEventId("1")))
        sse.send(SseMessage.Retry(Duration.ofSeconds(2)))
        sse.close()
    }
})
```

- Handles all HTTP methods (GET, POST, PUT, PATCH, DELETE, etc.) that produce `text/event-stream`
- Uses Spring's `SseEmitter` under the hood; sets unlimited timeout (`SseEmitter(0L)`)
- Maps all four `SseMessage` types: `Data`, `Event` (with optional `id` and `backoff`), `Retry`, and `Ping`

## WebSocket Handler

`SpringToHttp4kWebSocketHandler` implements Spring's `WebSocketHandler` to bridge an http4k `WsHandler`:

```kotlin
val handler = SpringToHttp4kWebSocketHandler { req ->
    WsResponse { ws ->
        ws.onMessage { ws.send(WsMessage("echo: " + it.bodyString())) }
        ws.onClose { println("closed: $it") }
        ws.onError { println("error: $it") }
    }
}
```

Register it with Spring's WebSocket config:

```kotlin
@Configuration
@EnableWebSocket
class WsConfig : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(handler, "/ws/**").setAllowedOrigins("*")
    }
}
```

- Manages per-session socket lifecycle using a `ConcurrentHashMap`
- Bridges both `TextMessage` and `BinaryMessage` payloads
- Reconstructs the http4k `Request` from the Spring `WebSocketSession` (URI path + query + handshake headers)

## Dependencies

`http4k-bridge-spring` depends on `http4k-realtime-core` for `SseHandler` and `WsHandler` types.

## Gotchas

- **Fallback only**: `SpringToHttp4kFallbackController` only receives requests Spring doesn't match first.
- **Jakarta servlet**: Uses `http4k-bridge-jakarta` internally for HTTP request/response conversion.
- **LensFailure handling**: Lens validation failures are automatically mapped to `400 BAD_REQUEST` with a `ProblemDetail` response body.
- **SSE controller is abstract**: You must subclass `SpringToHttp4kSseController`, not instantiate it directly.
- **WebSocket handler is concrete**: `SpringToHttp4kWebSocketHandler` can be instantiated directly with a `WsHandler` lambda.
- **No partial messages**: `SpringToHttp4kWebSocketHandler.supportsPartialMessages()` returns `false`.
