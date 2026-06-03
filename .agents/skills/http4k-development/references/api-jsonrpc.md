---
license: Apache-2.0
module: http4k-api-jsonrpc
---

# http4k-api-jsonrpc Reference

JSON-RPC 2.0 server with auto and manual marshalling modes. Supports batching, notifications, positional and named parameters.

## Auto Mode (Reflection-Based)

```kotlin
val counter = Counter()

val rpc: HttpHandler = JsonRpc.auto(Jackson) {
    method("increment", handler(counter::increment))
    method("current", handler(counter::currentValue))
}

// With explicit field names for positional (array) parameters
val rpc: HttpHandler = JsonRpc.auto(Jackson) {
    method("increment", handler(setOf("value"), counter::increment))
}

data class Increment(val value: Int)
```

Auto mode uses reflection to marshal parameters from the request into the handler's input type and serialize the return value.

## Manual Mode (Explicit Lenses)

```kotlin
val incrementParams = JsonRpcMapping<NODE, Increment> {
    Increment(json.textValueOf(it, "value")!!.toInt())
}
val intResult = JsonRpcMapping<Int, NODE> { json.number(it) }

val rpc: HttpHandler = JsonRpc.manual(json) {
    method("increment", handler(incrementParams, intResult, counter::increment))
    method("current", handler(intResult, counter::currentValue))

    // With positional parameter support
    method("inc", handler(setOf("value"), incrementParams, intResult, counter::increment))
}
```

## Custom Error Handling

```kotlin
val errorHandler: ErrorHandler = { error ->
    when (error) {
        is NegativeValueException -> object : ErrorMessage(1, "Negative value") {
            override fun <NODE> data(json: Json<NODE>): NODE = json.string("details here")
        }
        else -> null  // fall through to default handling
    }
}

val rpc: HttpHandler = JsonRpc.auto(Jackson, errorHandler) {
    method("increment", handler(counter::increment))
}
```

## Standard Error Codes

```kotlin
ErrorMessage.ParseError       // -32700
ErrorMessage.InvalidRequest   // -32600
ErrorMessage.MethodNotFound   // -32601
ErrorMessage.InvalidParams    // -32602
ErrorMessage.InternalError    // -32603
```

## Request/Response Format

```json
// Request (named params)
{"jsonrpc": "2.0", "method": "increment", "params": {"value": 5}, "id": "1"}

// Request (positional params)
{"jsonrpc": "2.0", "method": "increment", "params": [5], "id": "1"}

// Notification (no id — no response returned)
{"jsonrpc": "2.0", "method": "increment", "params": {"value": 5}}

// Batch request
[
  {"jsonrpc": "2.0", "method": "increment", "params": {"value": 5}, "id": "1"},
  {"jsonrpc": "2.0", "method": "current", "id": "2"},
  {"jsonrpc": "2.0", "method": "increment", "params": [2]}
]

// Success response
{"jsonrpc": "2.0", "result": 5, "id": "1"}

// Error response
{"jsonrpc": "2.0", "error": {"code": -32601, "message": "Method not found"}, "id": "1"}
```

## Gotchas

- **POST only**: Returns 405 for non-POST methods.
- **Content-Type**: Expects `application/json`.
- **Notifications return 204**: Requests without `id` are notifications — processed but no response body is returned (`204 NO_CONTENT`).
- **Batch with only notifications**: Returns `204 NO_CONTENT` with empty body.
- **Positional params**: Array parameters are mapped to named fields using `paramsFieldNames`. Without explicit field names, array params won't work in auto mode.
- **ErrorHandler returns null**: Return `null` from the error handler to fall through to default JSON-RPC error handling.
