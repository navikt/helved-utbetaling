---
license: Apache-2.0
module: http4k-connect-amazon-cognito-fake
---

# http4k-connect-amazon-cognito-fake Reference

In-memory fake Cognito server for testing authentication flows.

## Setup

```kotlin
val fakeCognito = FakeCognito()
val client = fakeCognito.client()
```

## Custom Configuration

```kotlin
val fakeCognito = FakeCognito(
    userPools = Storage.InMemory(),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern

```kotlin
val cognito = FakeCognito().client()

val poolId = cognito.createUserPool(PoolName.of("test-pool")).successValue().UserPool.Id

cognito.adminCreateUser(
    userPoolId = poolId,
    username = Username.of("test@example.com"),
    temporaryPassword = Password.of("Temp1234!")
).successValue()

val tokens = cognito.adminInitiateAuth(
    userPoolId = poolId,
    clientId = ClientId.of("test-client"),
    authFlow = AuthFlowType.ADMIN_NO_SRP_AUTH,
    authParameters = mapOf("USERNAME" to "test@example.com", "PASSWORD" to "Temp1234!")
).successValue().AuthenticationResult
```

## Chaos Testing

```kotlin
fakeCognito.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeCognito.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Fake JWKS returns pre-generated keys — tokens can be verified against them
- Auth flows that require challenge response (NEW_PASSWORD_REQUIRED) are supported
- User state (FORCE_CHANGE_PASSWORD, CONFIRMED) is tracked in storage
