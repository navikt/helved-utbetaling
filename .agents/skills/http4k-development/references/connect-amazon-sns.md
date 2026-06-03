---
license: Apache-2.0
module: http4k-connect-amazon-sns
---

# http4k-connect-amazon-sns Reference

SNS client — connect actions for Amazon Simple Notification Service.

## Client

```kotlin
val sns = SNS.Http(
    region = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient()   // optional
)
```

## Topic Operations

```kotlin
val topicArn = sns.createTopic(TopicName.of("my-topic")).successValue().TopicArn
sns.listTopics().successValue()
sns.deleteTopic(topicArn).successValue()
```

## Publish

```kotlin
sns.publishMessage(
    TopicArn = topicArn,
    Message = "Hello subscribers!",
    Subject = "Optional subject",
    MessageAttributes = mapOf(
        "eventType" to MessageAttribute("String", "order.created")
    )
).successValue()

// Batch publish
sns.publishBatch(
    TopicArn = topicArn,
    PublishBatchRequestEntries = listOf(
        PublishBatchRequestEntry("id-1", "Message 1"),
        PublishBatchRequestEntry("id-2", "Message 2")
    )
).successValue()
```

## Subscribe

```kotlin
sns.subscribe(
    TopicArn = topicArn,
    Protocol = "https",
    Endpoint = "https://myservice.example.com/webhook"
).successValue()
```

## Gotchas

- Uses **query protocol** (form-urlencoded POST) — different from SQS/DynamoDB JSON
- `Message` is a plain string — use structured JSON for event payloads
- Message attributes use special DTO format (`MessageAttribute("String", value)`)
- Subscription protocols: `http`, `https`, `email`, `sqs`, `lambda`, `application`, `firehose`
