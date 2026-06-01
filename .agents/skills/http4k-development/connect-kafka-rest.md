---
license: Apache-2.0
module: http4k-connect-kafka-rest
---

# http4k-connect-kafka-rest Reference

KafkaRest client — connect actions for Confluent Kafka REST Proxy (v2 and v3 APIs).

## Client

```kotlin
val kafkaRest = KafkaRest.Http(
    credentials = Credentials("api-key", "api-secret"),
    baseUri = Uri.of("https://pkc-xxxxx.us-east-1.aws.confluent.cloud"),
    http = JavaHttpClient()   // optional
)
```

## V3 API — Produce Records

```kotlin
// List clusters first
val clusterId = kafkaRest.listClusters().successValue().data.first().cluster_id

// Produce a message
kafkaRest.produceRecord(
    clusterId = ClusterId.of(clusterId),
    topicName = TopicName.of("my-topic"),
    key = ProduceRecordData(data = "message-key"),
    value = ProduceRecordData(data = mapOf("field" to "value"))
).successValue()
```

## V2 API — Consume Records

```kotlin
// Create consumer
val consumer = kafkaRest.createConsumer(
    groupName = ConsumerGroupName.of("my-group"),
    name = ConsumerName.of("consumer-1"),
    format = "json"
).successValue()

// Subscribe
kafkaRest.subscribeToTopics(
    groupName = ConsumerGroupName.of("my-group"),
    consumerName = ConsumerName.of("consumer-1"),
    topics = listOf("my-topic")
).successValue()

// Consume
val records = kafkaRest.consumeRecords(
    groupName = ConsumerGroupName.of("my-group"),
    consumerName = ConsumerName.of("consumer-1")
).successValue()
```

## Gotchas

- V2 and V3 are distinct APIs — V3 is preferred for Confluent Cloud
- `Credentials` takes API key and secret (not username/password for local Kafka)
- Consumer base URL from `createConsumer` must be used for subsequent consumer calls
- `Accept: application/vnd.kafka.json.v2+json` header required for V2 consume
- Commit offsets explicitly after processing to avoid redelivery
