---
license: Apache-2.0
module: http4k-connect-amazon-secretsmanager-fake
---

# http4k-connect-amazon-secretsmanager-fake Reference

In-memory fake Secrets Manager server for testing.

## Setup

```kotlin
val fakeSecrets = FakeSecretsManager()
val client = fakeSecrets.client()
```

## Custom Configuration

```kotlin
val fakeSecrets = FakeSecretsManager(
    secrets = Storage.InMemory(),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern

```kotlin
val secrets = FakeSecretsManager().client()

secrets.createSecret(SecretName.of("my-secret"), secretString = "value").successValue()
val retrieved = secrets.getSecretValue(SecretId.of("my-secret")).successValue().SecretString
assertThat(retrieved, equalTo("value"))
```

## Chaos Testing

```kotlin
fakeSecrets.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeSecrets.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Deletion recovery window is ignored in the fake — secrets are removed immediately
- Binary secrets stored as Base64
