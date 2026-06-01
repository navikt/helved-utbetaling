---
license: Apache-2.0
module: http4k-connect-amazon-cloudwatch
---

# http4k-connect-amazon-cloudwatch Reference

CloudWatch client — connect actions for Amazon CloudWatch metrics and alarms.

## Client

```kotlin
val cw = CloudWatch.Http(
    region = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient()   // optional
)
```

## Put Metrics

```kotlin
cw.putMetricData(
    namespace = Namespace.of("MyApp/Performance"),
    metricData = listOf(
        MetricDatum(
            metricName = MetricName.of("RequestLatency"),
            value = 42.5,
            unit = StandardUnit.Milliseconds,
            dimensions = listOf(Dimension("Environment", "production"))
        )
    )
).successValue()
```

## Get Metrics

```kotlin
val metrics = cw.getMetricData(
    metricDataQueries = listOf(
        MetricDataQuery(
            id = "m1",
            metricStat = MetricStat(
                metric = Metric(
                    namespace = Namespace.of("MyApp/Performance"),
                    metricName = MetricName.of("RequestLatency")
                ),
                period = 60,
                stat = "Average"
            )
        )
    ),
    startTime = Instant.now().minusSeconds(3600),
    endTime = Instant.now()
).successValue()
```

## Alarms

```kotlin
cw.putMetricAlarm(
    alarmName = AlarmName.of("HighLatencyAlarm"),
    metricName = MetricName.of("RequestLatency"),
    namespace = Namespace.of("MyApp/Performance"),
    threshold = 1000.0,
    comparisonOperator = ComparisonOperator.GreaterThanThreshold,
    evaluationPeriods = 3,
    period = 60,
    statistic = Statistic.Average
).successValue()

cw.describeAlarms().successValue()
```

## Gotchas

- Uses XML/query protocol (not JSON)
- `putMetricData` accepts up to 20 metrics per call
- Metric resolution: standard (60s minimum), high-resolution (1s minimum)
- Alarm state transitions trigger SNS notifications if configured
- Custom namespaces should use `MyApp/Category` convention (avoid `AWS/` prefix)
