---
license: Apache-2.0
module: http4k-connect-amazon-iamidentitycenter-fake
---

# http4k-connect-amazon-iamidentitycenter-fake Reference

In-memory fakes for IAM Identity Center: `FakeSSO` and `FakeOIDC`.

## Setup

```kotlin
val fakeSSO = FakeSSO()
val ssoClient = fakeSSO.client()

val fakeOIDC = FakeOIDC()
val oidcClient = fakeOIDC.client()
```

## Custom Configuration

```kotlin
val fakeSSO = FakeSSO(
    credentials = Storage.InMemory(),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
val fakeOIDC = FakeOIDC(
    registrations = Storage.InMemory(),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern — Device Auth Flow

```kotlin
val oidc = FakeOIDC().client()
val sso = FakeSSO().client()

val reg = oidc.registerClient(ClientName.of("test-cli"), ClientType.PUBLIC).successValue()
val device = oidc.startDeviceAuthorization(reg.ClientId, reg.ClientSecret, StartUrl.of("https://test.awsapps.com/start")).successValue()

// Simulate user approval (in real tests, the fake auto-approves)
val token = oidc.createToken(reg.ClientId, reg.ClientSecret, device.DeviceCode, GrantType.DEVICE_CODE).successValue().AccessToken

val credentials = sso.getFederatedCredentials(token, AccountId.of("123456789012"), RoleName.of("Admin")).successValue()
```

## Chaos Testing

```kotlin
fakeSSO.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeOIDC.returnStatus(Status.SERVICE_UNAVAILABLE)
```

## Gotchas

- Two separate fakes in one module: `FakeSSO` and `FakeOIDC`
- Both extend `ChaoticHttpHandler`
- Device authorization polling loop is auto-approved in the fake
