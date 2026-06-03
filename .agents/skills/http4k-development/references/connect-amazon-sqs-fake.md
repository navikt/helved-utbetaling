---
license: Apache-2.0
module: http4k-connect-amazon-sqs-fake
---

# http4k-connect-amazon-sqs-fake Reference

In-memory fake SQS server for testing.

## Setup

```kotlin
val fakeSqs = FakeSQS()
val client = fakeSqs.client()
```

## Custom Configuration

```kotlin
val fakeSqs = FakeSQS(
    queues = Storage.InMemory(),
    awsAccount = AwsAccount.of("123456789012"),
    region = Region.of("us-east-1")
)
```

## Test Contracts

```kotlin
class FakeSQSTest : SQSContract {
    override val sqs = FakeSQS().client()
}
```

## Chaos Testing

```kotlin
fakeSqs.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeSqs.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- MD5 checksums validated on receive
- Queue URLs generated as `http://localhost:{port}/{account}/{name}`
- FIFO queues supported
