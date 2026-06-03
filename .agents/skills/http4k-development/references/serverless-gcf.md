---
license: Apache-2.0
module: http4k-serverless-gcf
---

# http4k-serverless-gcf Reference

Google Cloud Functions adapter. Run http4k apps as GCF HTTP functions or event functions.

## HTTP Function

```kotlin
class MyFunction : GoogleCloudHttpFunction(AppLoader { env -> myApp })

// Or with a static handler
class MyFunction : GoogleCloudHttpFunction({ Response(OK).body("hello") })
```

## Event Function

```kotlin
class MyEventFunction : GoogleCloudEventFunction(FnLoader { env ->
    FnHandler { input, context ->
        // process event from input stream
        "ok".byteInputStream()
    }
})
```

## Accessing GCF Request

```kotlin
val app: HttpHandler = { req ->
    val gcfRequest = GCF_REQUEST_KEY(req)  // com.google.cloud.functions.HttpRequest
    Response(OK)
}
```

## Gotchas

- **Extend the abstract class**: GCF discovers your function by class name. Extend `GoogleCloudHttpFunction` and configure in `function.target`.
- **CatchAll filter**: The adapter wraps the handler with `CatchAll()` to prevent unhandled exceptions.
- **GCF request injected**: The original `com.google.cloud.functions.HttpRequest` is available via `GCF_REQUEST_KEY` lens.
- **Streaming body**: Request and response bodies are streamed via `InputStream`/`OutputStream`.
