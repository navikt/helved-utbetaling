---
license: Apache-2.0
module: http4k-connect-amazon-instancemetadata-fake
---

# http4k-connect-amazon-instancemetadata-fake Reference

In-memory fake EC2 Instance Metadata Service for testing.

## Setup

```kotlin
val fakeImds = FakeInstanceMetadataService()
val client = fakeImds.client()
```

## Custom Configuration

```kotlin
val fakeImds = FakeInstanceMetadataService(
    instanceId = InstanceId.of("i-1234567890abcdef0"),
    instanceType = InstanceType.of("t3.micro"),
    amiId = AmiId.of("ami-12345678"),
    region = Region.of("us-east-1"),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern

```kotlin
val imds = FakeInstanceMetadataService(
    instanceId = InstanceId.of("i-test-instance")
).client()

val id = imds.getInstanceId().successValue()
assertThat(id, equalTo(InstanceId.of("i-test-instance")))

val creds = imds.getSecurityCredentials(RoleName.of("test-role")).successValue()
```

## Chaos Testing

```kotlin
fakeImds.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeImds.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Useful for testing `CredentialsProvider.InstanceMetadata()` — point it at the fake
- Credentials returned are synthetic test values
