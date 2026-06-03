---
license: Apache-2.0
module: http4k-connect-amazon-lambda
---

# http4k-connect-amazon-lambda Reference

Lambda client — invoke AWS Lambda functions from http4k.

## Client

```kotlin
val lambda = Lambda.Http(
    region = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient()   // optional
)
```

## Invoke Function

```kotlin
data class MyRequest(val input: String)
data class MyResponse(val result: String)

val result: MyResponse = lambda.invokeFunction<MyResponse>(
    FunctionName.of("my-function"),
    MyRequest("hello")
).successValue()

println(result.result)
```

## Invocation Types

```kotlin
// Request/Response (default) — waits for result
lambda.invokeFunction<MyResponse>(name, req)

// Event — fire and forget (async)
lambda.invokeFunction<MyResponse>(name, req, invocationType = Event)

// Dry run — validate without executing
lambda.invokeFunction<MyResponse>(name, req, invocationType = DryRun)
```

## Gotchas

- Request and response are JSON-serialized (using Moshi by default)
- Log tail from Lambda execution is returned in `X-Amz-Log-Result` response header (base64 encoded)
- Uses REST API: `POST /2015-03-31/functions/{name}/invocations`
- `FakeLambda` requires the actual handler code via `FnLoader` — useful for local integration testing
