---
license: Apache-2.0
module: http4k-connect-amazon-scheduler
---

# http4k-connect-amazon-scheduler Reference

Scheduler client — connect actions for Amazon EventBridge Scheduler.

## Client

```kotlin
val scheduler = Scheduler.Http(
    region = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient()   // optional
)
```

## Schedule Groups

```kotlin
scheduler.createScheduleGroup(name = ScheduleGroupName.of("my-app")).successValue()
scheduler.getScheduleGroup(name = ScheduleGroupName.of("my-app")).successValue()
scheduler.listScheduleGroups().successValue()
scheduler.deleteScheduleGroup(name = ScheduleGroupName.of("my-app")).successValue()
```

## Schedules

```kotlin
// Create a cron schedule
scheduler.createSchedule(
    name = ScheduleName.of("daily-report"),
    groupName = ScheduleGroupName.of("my-app"),
    scheduleExpression = "cron(0 8 * * ? *)",
    target = Target(
        arn = "arn:aws:lambda:us-east-1:123456789:function:my-function",
        roleArn = "arn:aws:iam::123456789:role/scheduler-role",
        input = """{"reportType": "daily"}"""
    ),
    flexibleTimeWindow = FlexibleTimeWindow(FlexibleTimeWindowMode.OFF)
).successValue()

scheduler.getSchedule(name = ScheduleName.of("daily-report")).successValue()
scheduler.listSchedules().successValue()
scheduler.deleteSchedule(name = ScheduleName.of("daily-report")).successValue()
```

## Gotchas

- Uses JSON REST protocol (not X-Amz-Target)
- Schedules require an IAM role that EventBridge Scheduler can assume to invoke the target
- `cron()` expressions use EventBridge Scheduler syntax (6 fields, `?` for day-of-week/month)
- `rate()` expressions: `rate(5 minutes)`, `rate(1 hour)`
- One-time schedules use `at(yyyy-MM-ddTHH:mm:ss)` syntax
- Default group name is `"default"` — custom groups must be created first
