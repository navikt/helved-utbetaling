---
license: Apache-2.0
module: http4k-serverless-azure
---

# http4k-serverless-azure Reference

Azure Functions adapter. Run http4k apps as Azure HTTP-triggered functions.

## HTTP Function

```kotlin
class MyFunction : AzureFunction(AppLoader { env -> myApp }) {
    @FunctionName("myFunction")
    override fun handleRequest(
        @HttpTrigger(name = "req", methods = [HttpMethod.GET, HttpMethod.POST],
            authLevel = AuthorizationLevel.ANONYMOUS)
        req: HttpRequestMessage<Optional<String>>,
        ctx: ExecutionContext
    ) = handle(req, ctx)
}

// Or with a static handler
class MyFunction : AzureFunction({ Response(OK).body("hello") }) { ... }
```

## Accessing Azure Context

```kotlin
val app: HttpHandler = { req ->
    val azureReq = AZURE_REQUEST_KEY(req)     // HttpRequestMessage
    val azureCtx = AZURE_CONTEXT_KEY(req)     // ExecutionContext
    Response(OK).body("Function: ${azureCtx.functionName}")
}
```

## Gotchas

- **Override handleRequest**: Azure Functions requires the `@FunctionName` and `@HttpTrigger` annotations on the handler method. Call `handle(req, ctx)` from your override.
- **CatchAll filter**: The adapter wraps the handler with `CatchAll()`.
- **Context injection**: Both the Azure request and execution context are injected via request lenses.
