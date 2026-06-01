---
license: Apache-2.0
module: http4k-connect-amazon-scheduler-fake
---

# http4k-connect-amazon-scheduler-fake Reference

In-memory fake EventBridge Scheduler server for testing scheduled task management.

## Setup

```kotlin
val fakeScheduler = FakeScheduler()
val client = fakeScheduler.client()
```

## Custom Configuration

```kotlin
val fakeScheduler = FakeScheduler(
    schedules = Storage.InMemory(),
    scheduleGroups = Storage.InMemory(),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern

```kotlin
val scheduler = FakeScheduler().client()

scheduler.createScheduleGroup(ScheduleGroupName.of("test-group")).successValue()
scheduler.createSchedule(
    name = ScheduleName.of("test-schedule"),
    groupName = ScheduleGroupName.of("test-group"),
    scheduleExpression = "rate(5 minutes)",
    target = Target("arn:aws:lambda:us-east-1:123:function:fn", "arn:aws:iam::123:role/role"),
    flexibleTimeWindow = FlexibleTimeWindow(FlexibleTimeWindowMode.OFF)
).successValue()

scheduler.getSchedule(ScheduleName.of("test-schedule")).successValue()
```

## Chaos Testing

```kotlin
fakeScheduler.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeScheduler.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Schedules are stored but not executed in the fake
- Schedule expression validation is not enforced in the fake
