---
license: Apache-2.0
module: http4k-connect-amazon-firehose-fake
---

# http4k-connect-amazon-firehose-fake Reference

In-memory fake Kinesis Data Firehose server for testing.

## Setup

```kotlin
val fakeFirehose = FakeFirehose()
val client = fakeFirehose.client()
```

## Custom Configuration

```kotlin
val fakeFirehose = FakeFirehose(
    streams = Storage.InMemory(),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern

```kotlin
val firehose = FakeFirehose().client()

firehose.createDeliveryStream(DeliveryStreamName.of("test-stream"), DeliveryStreamType.DirectPut).successValue()
firehose.putRecord(DeliveryStreamName.of("test-stream"), Record("data".toByteArray())).successValue()

// Inspect stored records via storage to assert delivery
```

## Chaos Testing

```kotlin
fakeFirehose.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeFirehose.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Records are stored in memory — inspect storage to verify content
- No real delivery to S3/Redshift/Elasticsearch in the fake
