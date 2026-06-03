---
license: Apache-2.0
module: http4k-connect-amazon-systemsmanager-fake
---

# http4k-connect-amazon-systemsmanager-fake Reference

In-memory fake Systems Manager Parameter Store for testing.

## Setup

```kotlin
val fakeSsm = FakeSystemsManager()
val client = fakeSsm.client()
```

## Custom Configuration

```kotlin
val fakeSsm = FakeSystemsManager(
    parameters = Storage.InMemory(),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern

```kotlin
val ssm = FakeSystemsManager().client()

ssm.putParameter(ParameterName.of("/test/value"), "hello", ParameterType.String).successValue()
val value = ssm.getParameter(ParameterName.of("/test/value")).successValue().Parameter.Value
assertThat(value, equalTo("hello"))
```

## Chaos Testing

```kotlin
fakeSsm.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeSsm.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- SecureString encryption/decryption is simulated (no real KMS calls)
- Parameter versioning is tracked in storage
