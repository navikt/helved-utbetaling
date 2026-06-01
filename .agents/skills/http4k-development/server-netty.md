---
license: Apache-2.0
module: http4k-server-netty
---

# http4k-server-netty Reference

Netty server backend. Supports HTTP and WebSocket. Does NOT support SSE.

## Construction

```kotlin
val server = app.asServer(Netty(8000)).start()

// With explicit graceful shutdown timeout
val server = app.asServer(Netty(8000, StopMode.Graceful(Duration.ofSeconds(10)))).start()
```

Default stop mode is `Graceful(5 seconds)`. Default port is `8000`.

## WebSocket

Netty implements `PolyServerConfig` with WebSocket support:

```kotlin
Netty(8000).toServer(http, ws).start()
```

### Fragmented frames

Incoming fragmented messages (WebSocket continuation frames) are automatically reassembled into a single `WsMessage` before being delivered to handlers. The handler always receives a complete message regardless of how many frames it arrived in.

Outgoing `WsMessage` instances are automatically chunked into 64 KB frames. This is transparent to application code.

## Stop Modes

| Mode | Supported |
|---|---|
| `StopMode.Immediate` | No — throws `UnsupportedStopMode` |
| `StopMode.Graceful(duration)` | Yes (default, 5s) |

## Gotchas

- **Graceful only**: Netty throws `ServerConfig.UnsupportedStopMode` if you pass `StopMode.Immediate`. This is enforced in the constructor.
- **No SSE**: Passing an `SseHandler` throws `UnsupportedOperationException("Netty does not support sse")`.
- **No HTTP/2**: The stock Netty config does not include HTTP/2 codec setup.
- **Aggregates full request body**: Uses `HttpObjectAggregator(Int.MAX_VALUE)` — the entire request body is buffered in memory before the handler runs.
- **WebSocket backpressure**: The WebSocket channel handler buffers up to 1000 frames. Auto-read is disabled on the Netty channel until the `WsConsumer` has been invoked and message handlers are registered, then re-enabled. When the buffer fills, auto-read is disabled again (backpressure) and re-enabled once the buffer drains below 50%. This prevents both memory exhaustion and race conditions with handler registration.
- **WebSocket message ordering**: Frames are always buffered and drained with an exclusive lock on the app executor — order is guaranteed even under concurrent frame arrival.
