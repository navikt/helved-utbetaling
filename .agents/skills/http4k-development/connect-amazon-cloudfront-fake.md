---
license: Apache-2.0
module: http4k-connect-amazon-cloudfront-fake
---

# http4k-connect-amazon-cloudfront-fake Reference

In-memory fake CloudFront server for testing cache invalidation flows.

## Setup

```kotlin
val fakeCf = FakeCloudFront()
val client = fakeCf.client()
```

## Custom Configuration

```kotlin
val fakeCf = FakeCloudFront(
    invalidations = Storage.InMemory(),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern

```kotlin
val cf = FakeCloudFront().client()

val result = cf.createInvalidation(
    distributionId = DistributionId.of("DIST123"),
    paths = listOf("/index.html"),
    callerReference = CallerReference.of("test-ref")
).successValue()

assertThat(result.Invalidation.Status, equalTo("InProgress"))
```

## Chaos Testing

```kotlin
fakeCf.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeCf.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Invalidation status is immediately set to `Completed` in the fake (no async propagation)
- Duplicate `callerReference` values are rejected (as per real CloudFront)
