---
license: Apache-2.0
module: http4k-serverless-lambda
---

# http4k-serverless-lambda Reference

AWS Lambda adapters. Run http4k apps on API Gateway (V1, V2, REST), Application Load Balancer, or direct invocation.

## API Gateway V1

```kotlin
class MyFunction : ApiGatewayV1LambdaFunction(AppLoader { env ->
    val db = env["DATABASE_URL"] ?: error("missing")
    createApp(db)
})

// Or with a static handler
class MyFunction : ApiGatewayV1LambdaFunction({ Response(OK).body("hello") })
```

## API Gateway V2

```kotlin
class MyFunction : ApiGatewayV2LambdaFunction(AppLoader { env -> myApp })
```

## API Gateway REST

```kotlin
class MyFunction : ApiGatewayRestLambdaFunction(AppLoader { env -> myApp })
```

## Application Load Balancer

```kotlin
class MyFunction : ApplicationLoadBalancerLambdaFunction(AppLoader { env -> myApp })
```

## Direct Invocation

```kotlin
class MyFunction : InvocationLambdaFunction(AppLoader { env -> myApp })
```

## Accessing Lambda Context

```kotlin
val LAMBDA_CONTEXT_KEY = RequestKey.required<Context>("HTTP4K_LAMBDA_CONTEXT")
val LAMBDA_REQUEST_KEY = RequestKey.required<Map<String, Any>>("HTTP4K_LAMBDA_REQUEST")

val app: HttpHandler = { req ->
    val context = LAMBDA_CONTEXT_KEY(req)
    val remainingTime = context.remainingTimeInMillis
    Response(OK).body("Function: ${context.functionName}")
}
```

## FnLoader Pattern

```kotlin
// For custom event processing (non-HTTP)
val loader = ApiGatewayV1FnLoader(AppLoader { env -> myApp })

// Or construct directly
val loader = ApiGatewayV1FnLoader(myApp)
```

## Gotchas

- **Base64 encoding**: API Gateway V1/V2 and ALB adapters base64-encode response bodies by default (`isBase64Encoded: true`). REST adapter does not.
- **Cookie handling**: V2 extracts cookies from the `cookies` array; V1/REST use headers.
- **Multi-value headers**: REST adapter supports `multiValueQueryStringParameters` and `multiValueHeaders` for parameters with multiple values.
- **CatchAll filter**: All adapters wrap the handler with `CatchAll()` to prevent unhandled exceptions from crashing the Lambda.
- **Context injection**: Lambda context and raw request are injected into the http4k Request via request lenses.
