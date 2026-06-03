---
license: Apache-2.0
module: http4k-connect-amazon-cloudwatch-fake
---

# http4k-connect-amazon-cloudwatch-fake Reference

In-memory fake CloudWatch server for testing metrics and alarms.

## Setup

```kotlin
val fakeCw = FakeCloudWatch()
val client = fakeCw.client()
```

## Custom Configuration

```kotlin
val fakeCw = FakeCloudWatch(
    metrics = Storage.InMemory(),
    alarms = Storage.InMemory(),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern

```kotlin
val cw = FakeCloudWatch().client()

cw.putMetricData(
    namespace = Namespace.of("Test/Metrics"),
    metricData = listOf(MetricDatum(MetricName.of("Count"), 1.0))
).successValue()
```

## Chaos Testing

```kotlin
fakeCw.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeCw.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- `getMetricData` returns stored data points filtered by time range
- Alarm state changes are not automatically evaluated against metrics in the fake
