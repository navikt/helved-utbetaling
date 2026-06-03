---
license: Apache-2.0
module: http4k-serverless-openwhisk
---

# http4k-serverless-openwhisk Reference

Apache OpenWhisk adapter. Run http4k apps as OpenWhisk actions.

## Function

```kotlin
class MyFunction : OpenWhiskFunction(AppLoader { env -> myApp })
```

The function implements `(JsonObject) -> JsonObject` — OpenWhisk's native interface.

## Binary Body Detection

```kotlin
// Default: all responses are text
class MyFunction : OpenWhiskFunction(AppLoader { env -> myApp }, NonBinary)

// All responses are binary (base64-encoded)
class MyFunction : OpenWhiskFunction(AppLoader { env -> myApp }, Binary)
```

## Accessing OpenWhisk Request

```kotlin
val app: HttpHandler = { req ->
    val owRequest = OW_REQUEST_KEY(req)  // JsonObject (raw OpenWhisk event)
    Response(OK)
}
```

## Gotchas

- **JsonObject format**: OpenWhisk passes `__ow_method`, `__ow_path`, `__ow_query`, `__ow_headers`, `__ow_body` fields.
- **Query parameters**: Non-`__ow_` top-level fields are treated as query parameters.
- **Binary mode**: When using `Binary`, request bodies are base64-decoded and response bodies are base64-encoded.
- **Uses Gson**: The adapter uses Gson's `JsonObject` for OpenWhisk's JSON format.
