---
license: Apache-2.0
module: http4k-serverless-core
---

# http4k-serverless-core Reference

Core serverless abstractions. `FnHandler` is `(In, Ctx) -> Out` — the serverless equivalent of `HttpHandler`.

## Core Types

```kotlin
// Serverless function handler
fun interface FnHandler<In, Ctx, Out> : (In, Ctx) -> Out

// Serverless filter (decorates FnHandler)
fun interface FnFilter<In, Ctx, Out> : (FnHandler<In, Ctx, Out>) -> FnHandler<In, Ctx, Out>

// Loads a configured function from environment
typealias FnLoader<Ctx> = (Map<String, String>) -> FnHandler<InputStream, Ctx, InputStream>

// Loads an HttpHandler from environment
fun interface AppLoader : (Map<String, String>) -> HttpHandler
```

## Serverless Filters

```kotlin
// Report function execution metrics
ServerlessFilters.ReportFnTransaction<String, Context, Int>(clock) { tx ->
    println("${tx.request} -> ${tx.response} in ${tx.duration}")
}

// Add request tracing (Zipkin)
ServerlessFilters.RequestTracing<InputStream, Context, InputStream>()
```

## AppLoader

```kotlin
// Simple: ignore environment
val loader = AppLoader { myApp }

// With environment configuration
val loader = AppLoader { env ->
    val dbUrl = env["DATABASE_URL"] ?: error("DATABASE_URL required")
    createApp(dbUrl)
}
```

## Gotchas

- **FnHandler vs HttpHandler**: `FnHandler<InputStream, Ctx, InputStream>` is the raw serverless interface. Most adapters convert to/from `HttpHandler` via `AppLoader`.
- **Environment from System.getenv()**: Lambda functions, GCF, etc. pass `System.getenv()` to the loader.
- **Filter composition**: `FnFilter.then(handler)` works like http4k's `Filter.then(handler)`.
