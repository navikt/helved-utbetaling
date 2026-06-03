---
license: Apache-2.0
module: http4k-serverless-tencent
---

# http4k-serverless-tencent Reference

Tencent Cloud Functions adapter. Run http4k apps as Tencent Cloud SCF functions.

## Function

```kotlin
class MyFunction : TencentCloudFunction(AppLoader { env -> myApp })

// Or with a static handler
class MyFunction : TencentCloudFunction({ Response(OK).body("hello") })
```

## Accessing Tencent Context

```kotlin
val app: HttpHandler = { req ->
    val tencentReq = TENCENT_REQUEST_KEY(req)   // APIGatewayProxyRequestEvent
    val tencentCtx = TENCENT_CONTEXT_KEY(req)   // Context
    Response(OK)
}
```

## Gotchas

- **API Gateway event format**: Uses `APIGatewayProxyRequestEvent` with `httpMethod`, `path`, `headers`, `queryStringParameters`, `body`.
- **Response format**: Returns `APIGatewayProxyResponseEvent` with `statusCode`, `headers` (as JSONObject), `body`.
- **Context is optional**: The Tencent context may be null; only injected when present.
- **CatchAll filter**: The adapter wraps the handler with `CatchAll()`.
