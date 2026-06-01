---
license: Apache-2.0
module: http4k-realtime-core
---

# http4k-realtime-core Reference

WebSocket and Server-Sent Events (SSE) support. Follows the same "function" pattern as HTTP.

## WebSocket Types

```kotlin
typealias WsHandler = (Request) -> WsResponse
typealias WsConsumer = (Websocket) -> Unit
fun interface WsFilter : (WsHandler) -> WsHandler

interface Websocket {
    fun send(message: WsMessage)
    fun close(status: WsStatus = NORMAL)
    fun onError(fn: (Throwable) -> Unit)
    fun onClose(fn: (WsStatus) -> Unit)
    fun onMessage(fn: (WsMessage) -> Unit)
}

interface WsClient : AutoCloseable {
    fun received(): Sequence<WsMessage>
    fun send(message: WsMessage)
    fun close(status: WsStatus)
}
```

## SSE Types

```kotlin
typealias SseHandler = (Request) -> SseResponse
typealias SseConsumer = (Sse) -> Unit
fun interface SseFilter : (SseHandler) -> SseHandler

interface Sse : AutoCloseable {
    val connectRequest: Request
    fun send(message: SseMessage): Sse
    fun close()
    fun onClose(fn: () -> Unit): Sse
}

interface SseClient : AutoCloseable {
    fun received(): Sequence<SseMessage>
}
```

## WebSocket Handler

```kotlin
val ws: WsHandler = { request: Request ->
    WsResponse { ws: Websocket ->
        ws.onMessage { message ->
            ws.send(WsMessage("Echo: ${message.bodyString()}"))
        }
        ws.onClose { status ->
            println("Closed: $status")
        }
    }
}
```

## SSE Handler

```kotlin
val sse: SseHandler = { request: Request ->
    SseResponse { sse: Sse ->
        sse.send(SseMessage.Data("connected"))
        sse.send(SseMessage.Event("update", "payload data"))
        sse.send(SseMessage.Event("update", "more data", id = SseEventId("2")))
        sse.close()
    }
}
```

## SSE Message Types

```kotlin
SseMessage.Data("plain data")                       // data: plain data
SseMessage.Data("binary".byteInputStream())         // base64-encoded
SseMessage.Event("eventName", "data")               // event: eventName\ndata: data
SseMessage.Event("evt", "data", SseEventId("1"))    // includes id: 1
SseMessage.Event("evt", "data", backoff = Duration.ofSeconds(5))  // includes retry: 5000
SseMessage.Retry(Duration.ofMinutes(1))             // retry: 60000
SseMessage.Ping                                      // :\n\n (keep-alive)
```

Multiline data is automatically split into separate `data:` lines in the wire format.

## WsMessage

```kotlin
WsMessage("text content")                           // Text mode
WsMessage("binary".toByteArray())                   // Binary mode
WsMessage(byteBuffer)                               // Binary mode
WsMessage(inputStream)                              // Binary mode

message.bodyString()                                // Read as String
message.body                                        // Access Body
```

## Routing

```kotlin
// WebSocket routing
val wsApp = websockets(
    "/chat/{room}" bind websockets { ws ->
        ws.send(WsMessage("joined"))
        ws.onMessage { ws.send(it) }
    },
    orElse bind websockets { it.close() }
)

// SSE routing
val sseApp = sse(
    "/events/{type}" bind sse(
        GET to { request -> SseResponse { it.send(SseMessage.Data(request.path("type")!!)); it.close() } }
    )
)

// Router predicates work the same as HTTP
websockets(
    query("token") bind websockets { /* authenticated */ },
    orElse bind websockets { it.close(WsStatus.REFUSE) }
)
```

## Filters

```kotlin
// WsFilter — same pattern as HTTP Filter
val auth = WsFilter { next ->
    { request ->
        if (request.header("Authorization") != null) next(request)
        else WsResponse { it.close(WsStatus.REFUSE) }
    }
}

val app = auth.then(wsHandler)

// Built-in filters
ServerFilters.InitialiseWsRequestContext(contexts)  // Request context for WS
ServerFilters.SetWsSubProtocol("graphql-ws")        // Set subprotocol
```

## PolyHandler (Combined HTTP + WS + SSE)

```kotlin
val app = poly(
    routes("/api" bind GET to apiHandler),
    "/ws" bindWs websockets { ws -> ws.onMessage { ws.send(it) } },
    "/events" bindSse sse { it.send(SseMessage.Data("hello")); it.close() }
)
```

Filters apply independently per protocol:

```kotlin
val decorated = httpFilter.then(
    sseFilter.then(
        wsFilter.then(polyHandler)
    )
)
```

## PolyFilters

Apply cross-cutting concerns across all protocol handlers in a `PolyHandler`. Compose with `.then()`.

```kotlin
// Error handling across all protocols
val safe = PolyFilters.CatchAll(
    onErrorHttp = { e -> Response(INTERNAL_SERVER_ERROR).body(e.message.orEmpty()) },
    onErrorSse = { e -> SseResponse(INTERNAL_SERVER_ERROR) { it.close() } },
    onErrorWs = { e -> WsResponse { it.close() } }
)

// Apply Security to all protocols at once
val secured = PolyFilters.Security(mySecurity)

// Transaction reporting with per-protocol labelers
val reported = PolyFilters.ReportTransaction(
    clock = Clock.systemUTC(),
    httpTransactionLabeler = { it },
    sseTransactionLabeler = { it },
    wsTransactionLabeler = { it },
    recordHttpFn = { tx -> println(tx) },
    recordSseFn = { tx -> println(tx) },
    recordWsFn = { tx -> println(tx) }
)

// Events overload — emits HttpEvent/SseEvent/WsEvent
val reported2 = PolyFilters.ReportTransaction(events)

// Combined HTTP CORS + SSE rebind protection
val cors = PolyFilters.CorsAndRebindProtection(corsPolicy)
```

Compose filters before applying to a `PolyHandler`:

```kotlin
val app = safe.then(secured.then(polyHandler))
```

> **Deprecation**: `ServerFilters.CorsAndRebindProtection()` is deprecated. Use `PolyFilters.CorsAndRebindProtection()` instead.

## Testing

Test WebSocket and SSE handlers in-memory — no server needed.

### WebSocket Testing

```kotlin
// testWsClient — returns a WsClient for asserting received messages
val client = wsHandler.testWsClient(Request(GET, "/chat/room1"))
val messages = client.received().toList()
assertThat(messages, equalTo(listOf(WsMessage("joined"))))

// testWebsocket — returns a Websocket for bidirectional testing
val ws = wsHandler.testWebsocket(Request(GET, "/"))
val received = mutableListOf<WsMessage>()
ws.onMessage { received += it }
ws.send(WsMessage("hello"))  // triggers server-side onMessage
```

### SSE Testing

```kotlin
val client = sseHandler.testSseClient(Request(GET, "/events"))
assertThat(client.status, equalTo(OK))
val messages = client.received().toList()
assertThat(messages, equalTo(listOf(SseMessage.Data("connected"))))
```

### PolyHandler Testing

```kotlin
val poly = PolyHandlerTestClient(polyHandler)
poly.http(Request(GET, "/api"))           // test HTTP
poly.ws(Request(GET, "/ws"))              // test WS (returns TestWsClient)
poly.sse(Request(GET, "/events"))         // test SSE (returns TestSseClient)
```

### toHttpHandler() — SSE as Plain HTTP

Convert a `PolyHandler` into an `HttpHandler` so SSE endpoints can be tested with a standard HTTP client or recording infrastructure:

```kotlin
val httpHandler = polyHandler.toHttpHandler()

// SSE requests (Accept: text/event-stream) are forwarded to the SSE handler
// and streamed back as a byte stream via piped I/O — persistent streams work
val response = httpHandler(Request(GET, "/events").header("Accept", "text/event-stream"))

// Terminating SSE stream — read all events from bodyString()
assertThat(response.bodyString(), equalTo("event: first\ndata: a\n\nevent: second\ndata: b\n\n"))

// Persistent SSE stream — read events incrementally without deadlock
val reader = response.body.stream.bufferedReader(Charsets.UTF_8)
val firstEvent = reader.readLine()  // blocks only until first event arrives
```

Non-SSE requests are forwarded to the HTTP handler unchanged.

### useClient Helper

```kotlin
wsHandler.testWsClient(Request(GET, "/")).useClient {
    send(WsMessage("hello"))
    val response = received().first()
    assertThat(response, equalTo(WsMessage("Echo: hello")))
}  // auto-closes
```

## SSE Headers

```kotlin
// Last-Event-ID — track reconnect position (per SSE spec)
val lastId: SseEventId? = Header.LAST_EVENT_ID(request)

// X-Accel-Buffering — control Nginx proxy buffering for SSE
// Set to "no" on the response to prevent Nginx from buffering the SSE stream
val buffering: XAccelBuffering = Header.X_ACCEL_BUFFERING(response)  // defaults to XAccelBuffering.no
response.with(Header.X_ACCEL_BUFFERING of XAccelBuffering.no)

enum class XAccelBuffering { yes, no }
```

Setting `X-Accel-Buffering: no` is required when an Nginx reverse proxy sits in front of an SSE endpoint — without it, Nginx buffers the entire response and the client sees no events until the connection closes.

## Gotchas

- **WsClient close semantics**: Closing with `WsStatus.NORMAL` ends the `received()` sequence. Closing with any other status throws `ClosedWebsocket(status)` when reading.
- **Messages don't echo**: A client does NOT receive messages it sends. Messages flow one direction through each side's handlers.
- **SSE is server-push only**: `Sse.send()` pushes to client. There is no client-to-server message channel (use HTTP for that).
- **SSE binary data is base64-encoded**: SSE is text-only. Binary data passed to `SseMessage.Data(InputStream)` is automatically base64-encoded.
- **SSE multiline data**: Data containing newlines is automatically split into multiple `data:` lines per the SSE spec.
- **Filter order matters**: Filters nest like HTTP filters — `first.then(second).then(handler)` means `first` processes the request first.
- **WsStatus equality uses code only**: Two `WsStatus` values with the same code but different descriptions are equal.
- **WsResponse subprotocol**: Set via `WsResponse("protocol") { ws -> ... }` or `ServerFilters.SetWsSubProtocol("protocol")`.
- **PolyFilters apply across all protocols**: `PolyFilters` apply concerns across all protocol handlers in a `PolyHandler`. They compose with `.then()`.
- **`toHttpHandler()` streams SSE via piped I/O**: Persistent SSE streams (that don't close immediately) are forwarded incrementally via background thread + `PipedInputStream`. Reading from `body.stream` returns events as they arrive without deadlock. Reading `bodyString()` blocks until the SSE handler closes the connection.
