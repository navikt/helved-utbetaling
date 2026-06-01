---
license: Apache-2.0
module: http4k-bridge-jakarta
---

# http4k-bridge-jakarta Reference

Bridge http4k handlers into Jakarta Servlet containers (Java EE 9+) and JAX-RS resources.

## Jakarta Servlet

```kotlin
val adapter = Http4kJakartaServletAdapter(myApp)
adapter.handle(jakartaRequest, jakartaResponse)
```

## JAX-RS Resource

```kotlin
@Path("/{.*}")
class Http4kResource : JakartaToHttp4kResource() {
    // http field injected via @Inject
}
```

Catches all unmatched JAX-RS routes and delegates to the http4k handler.

## Conversion Functions

```kotlin
val request: Request? = jakartaRequest.asHttp4kRequest()
response.transferTo(jakartaResponse)
```

## Gotchas

- **jakarta.servlet**: Uses `jakarta.servlet` (Java EE 9+). For `javax.servlet`, use `http4k-bridge-servlet`.
- **Optional request**: `asHttp4kRequest()` returns `null` for unsupported HTTP methods, resulting in `501 NOT_IMPLEMENTED`.
- **JAX-RS injection**: The `http` field uses `@Inject` — configure your DI container to provide the `HttpHandler`.
