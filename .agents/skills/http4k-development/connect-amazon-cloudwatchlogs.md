---
license: Apache-2.0
module: http4k-connect-amazon-cloudwatchlogs
---

# http4k-connect-amazon-cloudwatchlogs Reference

CloudWatchLogs client — connect actions for Amazon CloudWatch Logs.

## Client

```kotlin
val cwLogs = CloudWatchLogs.Http(
    region = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient()   // optional
)
```

## Log Group & Stream Setup

```kotlin
cwLogs.createLogGroup(logGroupName = LogGroupName.of("/myapp/production")).successValue()
cwLogs.createLogStream(
    logGroupName = LogGroupName.of("/myapp/production"),
    logStreamName = LogStreamName.of("instance-1")
).successValue()
```

## Put Log Events

```kotlin
cwLogs.putLogEvents(
    logGroupName = LogGroupName.of("/myapp/production"),
    logStreamName = LogStreamName.of("instance-1"),
    logEvents = listOf(
        InputLogEvent(
            timestamp = System.currentTimeMillis(),
            message = "Application started"
        )
    )
).successValue()
```

## Query Logs

```kotlin
val events = cwLogs.filterLogEvents(
    logGroupName = LogGroupName.of("/myapp/production"),
    filterPattern = "ERROR",
    startTime = System.currentTimeMillis() - 3600000,
    endTime = System.currentTimeMillis()
).successValue().Events
```

## Gotchas

- Uses JSON protocol with `X-Amz-Target` header
- `putLogEvents` requires events sorted by timestamp (ascending)
- Sequence token required for subsequent `putLogEvents` to same stream (returned by previous put)
- Log groups have retention policies — set with `putRetentionPolicy`
- `filterLogEvents` uses CloudWatch Logs filter pattern syntax (not regex)
