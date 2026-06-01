---
license: Apache-2.0
module: http4k-connect-core
---

# http4k-connect-core Reference

Core abstractions for the http4k-connect pattern — typed actions over HTTP.

## Action Pattern

```kotlin
interface Action<out R> {
    fun toRequest(): Request
    fun toResult(response: Response): R
}
```

All connect clients expose actions as extension functions on the client interface:

```kotlin
val result: Result<MyResponse, RemoteFailure> = client.myAction(param1, param2)
result.successValue()  // unwrap or throw
```

## Base Action Classes

```kotlin
// For actions returning non-null JSON responses
abstract class NonNullAutoMarshalledAction<R : Any>(clazz, autoMarshalling) :
    Action<Result<R, RemoteFailure>>

// For actions where 404 → Success(null)
abstract class NullableAutoMarshalledAction<R : Any>(clazz, autoMarshalling) :
    Action<Result<R?, RemoteFailure>>

// For plain text responses
abstract class PlainTextAction : Action<Result<String, RemoteFailure>>
```

## RemoteFailure

```kotlin
data class RemoteFailure(
    val method: Method,
    val uri: Uri,
    val status: Status,
    val message: String? = null
)
```

## Error Handling

```kotlin
when (val result = client.myAction(param)) {
    is Success -> result.value
    is Failure -> when (result.reason.status) {
        Status.NOT_FOUND -> null
        else -> error("Request failed: ${result.reason}")
    }
}

// Or simply:
val value = result.successValue()   // throws on Failure
val value = result.orNull()         // null on Failure
```

## AWS Auth Filter (for Amazon services)

```kotlin
val http = ClientFilters.AwsAuth(
    scope = AwsCredentialScope(region, serviceName),
    credentialsProvider = CredentialsProvider.Environment(),
    clock = Clock.systemUTC(),
    payloadMode = Payload.Mode.Signed
).then(JavaHttpClient())
```

## Credentials Provider

```kotlin
// From environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN)
val creds = CredentialsProvider.Environment()

// From explicit map
val creds = CredentialsProvider.Environment(mapOf(
    "AWS_ACCESS_KEY_ID" to "key",
    "AWS_SECRET_ACCESS_KEY" to "secret"
))
```
