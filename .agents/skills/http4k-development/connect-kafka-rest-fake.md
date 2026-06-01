---
license: Apache-2.0
module: http4k-connect-kafka-rest-fake
---

# http4k-connect-kafka-rest-fake Reference

In-memory fake Kafka REST Proxy server for testing.

## Setup

```kotlin
val fakeKafka = FakeKafkaRest()
val client = fakeKafka.client()
```

## Custom Configuration

```kotlin
val fakeKafka = FakeKafkaRest(
    topics = Storage.InMemory(),
    consumers = Storage.InMemory(),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern

```kotlin
val kafkaRest = FakeKafkaRest().client()

val clusterId = kafkaRest.listClusters().successValue().data.first().cluster_id

kafkaRest.produceRecord(
    ClusterId.of(clusterId),
    TopicName.of("test-topic"),
    value = ProduceRecordData(data = mapOf("key" to "value"))
).successValue()
```

## Chaos Testing

```kotlin
fakeKafka.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeKafka.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Messages are stored in memory — inspect storage to verify produced records
- Consumer offsets are tracked in memory
