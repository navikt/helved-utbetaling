---
license: Apache-2.0
module: http4k-serverless-lambda-runtime
---

# http4k-serverless-lambda-runtime Reference

AWS Lambda custom runtime. Run http4k Lambda functions with the Lambda Runtime API (for custom runtimes or GraalVM native images).

## Usage

```kotlin
fun main() {
    AwsLambdaRuntime()
        .asServer(ApiGatewayV1FnLoader(AppLoader { env -> myApp }))
        .start()
}

// Or with any other FnLoader
fun main() {
    AwsLambdaRuntime()
        .asServer(ApiGatewayV2FnLoader(myApp))
        .start()
}
```

## How It Works

1. Reads `AWS_LAMBDA_RUNTIME_API` from environment
2. Polls the Runtime API for invocations (`GET /runtime/invocation/next`)
3. Passes the event to the `FnLoader`-created handler
4. Posts the result back (`POST /runtime/invocation/{id}/response`)
5. Reports errors to the Runtime API if the handler throws

## Lambda Context

The runtime constructs a `LambdaEnvironmentContext` from the invocation headers and environment variables, providing:
- `awsRequestId` from the invocation
- `functionName`, `functionVersion`, `logGroupName`, `logStreamName` from environment
- `remainingTimeInMillis` from the deadline header

## Gotchas

- **Custom runtime entry point**: Use this as the `main()` function in your `bootstrap` executable.
- **No port binding**: `port()` throws `UnsupportedOperationException` — this is not a traditional server.
- **Init error reporting**: If the `FnLoader` throws during initialization, the error is reported to the Runtime API's `/init/error` endpoint.
- **Runs in a loop**: The runtime polls continuously until `stop()` is called.
