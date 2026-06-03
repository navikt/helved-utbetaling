---
license: Apache-2.0
module: http4k-bridge-micronaut
---

# http4k-bridge-micronaut Reference

Bridge http4k handlers into Micronaut as a fallback controller.

## Micronaut Controller

```kotlin
@Controller("/")
class Http4kFallback(override val http4k: HttpHandler) : MicronautToHttp4kFallbackController
```

## How It Works

- Interface provides handlers for all HTTP methods (`@Get`, `@Post`, `@Put`, etc.)
- Both `/` and `/{+path}` variants catch all paths
- Converts between Micronaut's `HttpRequest<InputStream>` and http4k types

## Gotchas

- **Interface-based**: Implement `MicronautToHttp4kFallbackController` and provide the `http4k` handler.
- **All methods covered**: Separate handler methods for GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD, TRACE.
- **Body handling**: Request body is `InputStream`-based with `getOrNull()` fallback for empty bodies.
