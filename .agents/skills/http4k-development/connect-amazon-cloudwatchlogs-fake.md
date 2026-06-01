---
license: Apache-2.0
module: http4k-connect-amazon-cloudwatchlogs-fake
---

# http4k-connect-amazon-cloudwatchlogs-fake Reference

In-memory fake CloudWatch Logs server for testing.

## Setup

```kotlin
val fakeCwLogs = FakeCloudWatchLogs()
val client = fakeCwLogs.client()
```

## Custom Configuration

```kotlin
val fakeCwLogs = FakeCloudWatchLogs(
    logGroups = Storage.InMemory(),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern

```kotlin
val cwLogs = FakeCloudWatchLogs().client()

cwLogs.createLogGroup(LogGroupName.of("/test/app")).successValue()
cwLogs.createLogStream(LogGroupName.of("/test/app"), LogStreamName.of("stream-1")).successValue()
cwLogs.putLogEvents(
    LogGroupName.of("/test/app"),
    LogStreamName.of("stream-1"),
    listOf(InputLogEvent(System.currentTimeMillis(), "test message"))
).successValue()
```

## Chaos Testing

```kotlin
fakeCwLogs.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeCwLogs.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Sequence token management is handled automatically in the fake
- Filter pattern matching is simplified compared to real CloudWatch Logs
