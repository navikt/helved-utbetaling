---
license: Apache-2.0
module: http4k-connect-amazon-sts-fake
---

# http4k-connect-amazon-sts-fake Reference

In-memory fake STS server for testing role assumption.

## Setup

```kotlin
val fakeSts = FakeSTS()
val client = fakeSts.client()
```

## Custom Configuration

```kotlin
val fakeSts = FakeSTS(
    clock = Clock.systemUTC(),
    random = Random(42),              // seed for deterministic session tokens
    defaultSessionValidity = Duration.ofHours(1)
)
```

## Inspecting Assumed Roles

`FakeSTS` accepts an optional `assumedRoles: Storage<AssumedRole>` for asserting which roles were assumed in tests:

```kotlin
val assumedRoles = Storage.InMemory<AssumedRole>()
val fakeSts = FakeSTS(assumedRoles = assumedRoles)
val sts = fakeSts.client()

val creds = sts.assumeRole(
    ARN.of("arn:aws:iam::123456789012:role/TestRole"),
    "test-session"
).successValue().Credentials

// Inspect stored assumed roles
val stored = assumedRoles.keySet().map { assumedRoles[it] }
assertThat(stored.first()!!.sessionName, equalTo("test-session"))
```

## Test Pattern

```kotlin
val sts = FakeSTS().client()

// Assume role
val result = sts.assumeRole(
    ARN.of("arn:aws:iam::123456789012:role/TestRole"),
    "test-session"
).successValue()
val creds = result.Credentials
// Use creds to create other fake clients with assumed credentials

// Get caller identity — requires using credentials from a prior assumeRole call
// GetCallerIdentity returns 401 if the credentials are not from an assumed role
val credentialedClient = STS.Http(creds, region, credentialedHttpClient)
val identity = credentialedClient.getCallerIdentity().successValue()
```

## Chaos Testing

```kotlin
fakeSts.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeSts.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Returns deterministic fake credentials (not real AWS credentials)
- Expiry set based on `defaultSessionValidity` from the clock
- Does **not** validate role ARNs or permissions
- **`getCallerIdentity` requires assumed-role credentials**: It returns `401 UNAUTHORIZED` for callers that did not obtain credentials via `assumeRole` or `assumeRoleWithWebIdentity` on this same fake instance
