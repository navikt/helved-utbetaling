---
license: Apache-2.0
module: http4k-connect-amazon-containercredentials-fake
---

# http4k-connect-amazon-containercredentials-fake Reference

In-memory fake container credentials endpoint for testing ECS/EKS credential flows.

## Setup

```kotlin
val fakeCreds = FakeContainerCredentials()
val client = fakeCreds.client()
```

## Custom Configuration

```kotlin
val fakeCreds = FakeContainerCredentials(
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern

```kotlin
val containerCreds = FakeContainerCredentials().client()

val credentials = containerCreds.getCredentials().successValue()
assertThat(credentials.AccessKeyId, present())
```

## Chaos Testing

```kotlin
fakeCreds.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeCreds.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Useful for testing `CredentialsProvider.Container()` — configure it to point at the fake server
- Credentials returned are synthetic test values with configurable expiry
