---
license: Apache-2.0
module: http4k-connect-amazon-sns-fake
---

# http4k-connect-amazon-sns-fake Reference

In-memory fake SNS server for testing.

## Setup

```kotlin
val fakeSns = FakeSNS()
val client = fakeSns.client()
```

## Custom Configuration

```kotlin
val fakeSns = FakeSNS(
    topics = Storage.InMemory(),
    awsAccount = AwsAccount.of("123456789012"),
    region = Region.of("us-east-1")
)
```

## Verify Published Messages

```kotlin
val messages = fakeSns.listMessages(TopicName.of("my-topic"))
assertThat(messages, hasSize(1))
assertThat(messages.first().Message, equalTo("Hello subscribers!"))
```

## Chaos Testing

```kotlin
fakeSns.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeSns.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- `listMessages` is a test helper — not part of the SNS API
- Messages are stored in-memory and not actually delivered to subscription endpoints
