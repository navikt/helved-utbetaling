---
license: Apache-2.0
module: http4k-serverless-alibaba
---

# http4k-serverless-alibaba Reference

Alibaba Cloud Functions adapter. Run http4k apps as Alibaba Cloud HTTP or event functions.

## HTTP Function

```kotlin
class MyFunction : AlibabaCloudHttpFunction(AppLoader { env -> myApp })

// Or with a static handler
class MyFunction : AlibabaCloudHttpFunction({ Response(OK).body("hello") })
```

## Event Function

```kotlin
class MyEventFunction : AlibabaCloudEventFunction(FnLoader { env ->
    FnHandler { input, context ->
        // process event
        "ok".byteInputStream()
    }
})
```

## Accessing Alibaba Context

```kotlin
val app: HttpHandler = { req ->
    val alibabaReq = ALIBABA_REQUEST_KEY(req)   // HttpServletRequest
    val alibabaCtx = ALIBABA_CONTEXT_KEY(req)   // Context
    Response(OK)
}
```

## Gotchas

- **Servlet-based**: Alibaba Cloud Functions uses `HttpServletRequest`/`HttpServletResponse` (javax.servlet).
- **Context is optional**: The Alibaba context may be null; it's only injected when present.
- **CatchAll filter**: The adapter wraps the handler with `CatchAll()`.
