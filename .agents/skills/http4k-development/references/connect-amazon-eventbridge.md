---
license: Apache-2.0
module: http4k-connect-amazon-eventbridge
---

# http4k-connect-amazon-eventbridge Reference

EventBridge client — connect actions for Amazon EventBridge event bus.

## Client

```kotlin
val eb = EventBridge.Http(
    region = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient()   // optional
)
```

## Event Bus Management

```kotlin
eb.createEventBus(name = EventBusName.of("my-app-events")).successValue()
eb.listEventBuses().successValue()
eb.deleteEventBus(name = EventBusName.of("my-app-events")).successValue()
```

## Put Events

```kotlin
eb.putEvents(
    entries = listOf(
        PutEventsRequestEntry(
            source = "com.myapp.orders",
            detailType = "OrderCreated",
            detail = """{"orderId": "123", "amount": 99.99}""",
            eventBusName = EventBusName.of("my-app-events")
        )
    )
).successValue()
```

## Gotchas

- Uses JSON protocol with `X-Amz-Target` header
- `putEvents` accepts up to 10 events per call
- `detail` must be a valid JSON string (not nested object — serialise first)
- Default event bus name is `"default"` — custom buses must be created first
- Failed entries in the response do NOT throw — check `FailedEntryCount` and `Entries[].ErrorCode`
- Event pattern matching is done by EventBridge rules (not the client)
