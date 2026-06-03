---
license: Apache-2.0
module: http4k-connect-amazon-lambda-fake
---

# http4k-connect-amazon-lambda-fake Reference

Fake Lambda server that runs actual handler code locally for integration testing.

## Setup

```kotlin
// FakeLambda runs the actual Lambda handler code
val fnLoader: FnLoader<Context> = FnLoader.Classpath()

val fakeLambda = FakeLambda(
    fnLoader = fnLoader,
    clock = Clock.systemUTC(),
    env = mapOf("MY_ENV_VAR" to "value")
)

val client = fakeLambda.client()
// Or:
val client = Lambda.Http(Region.of("us-east-1"), CredentialsProvider.Environment(testEnv), fakeLambda)
```

## How It Works

`FakeLambda` loads handler classes from the classpath and invokes them with the request JSON. The handler must be registered via `FnLoader` which typically points to your `AppLoader` or `FnLoader` implementation:

```kotlin
// Your Lambda handler:
class MyFunction : FnLoader<Context> {
    override fun invoke(env: Map<String, String>): HttpHandler = { req ->
        Response(OK).body("Processed: ${req.bodyString()}")
    }
}
```

## Test Pattern

```kotlin
class MyFunctionTest {
    val fake = FakeLambda(MyFunction())
    val client = fake.client()

    @Test fun `processes request`() {
        val result = client.invokeFunction<MyResponse>(
            FunctionName.of("my-function"),
            MyRequest("input")
        ).successValue()
        assertThat(result.result, equalTo("Processed: input"))
    }
}
```

## Chaos Testing

```kotlin
fakeLambda.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeLambda.behave()
```

## Gotchas

- **Requires actual handler code** — unlike other fakes that simulate behavior
- Extends `ChaoticHttpHandler`
- `Context` provides function name, remaining time, etc. to the handler
- Environment variables set on `FakeLambda` are passed to the handler via `env` map
