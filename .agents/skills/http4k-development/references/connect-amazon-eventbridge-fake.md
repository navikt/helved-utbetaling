---
license: Apache-2.0
module: http4k-connect-amazon-eventbridge-fake
---

# http4k-connect-amazon-eventbridge-fake Reference

In-memory fake EventBridge server for testing event publishing.

## Setup

```kotlin
val fakeEb = FakeEventBridge()
val client = fakeEb.client()
```

## Custom Configuration

```kotlin
val fakeEb = FakeEventBridge(
    eventBuses = Storage.InMemory(),
    events = Storage.InMemory(),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern

```kotlin
val eb = FakeEventBridge().client()

eb.createEventBus(EventBusName.of("test-bus")).successValue()
eb.putEvents(listOf(
    PutEventsRequestEntry(
        source = "test.source",
        detailType = "TestEvent",
        detail = """{"key": "value"}""",
        eventBusName = EventBusName.of("test-bus")
    )
)).successValue()

// Inspect stored events via storage to assert publishing
```

## Chaos Testing

```kotlin
fakeEb.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeEb.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Events are stored but not routed to rules/targets in the fake
- Use storage inspection to verify events were published with correct content
