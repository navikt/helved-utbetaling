---
license: Apache-2.0
module: http4k-connect-google-analytics-ga4-fake
---

# http4k-connect-google-analytics-ga4-fake Reference

In-memory fake Google Analytics GA4 server for testing analytics event sending.

## Setup

```kotlin
val fakeGa4 = FakeGoogleAnalytics()
val client = fakeGa4.client()
```

## Custom Configuration

```kotlin
val fakeGa4 = FakeGoogleAnalytics(
    events = Storage.InMemory(),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern

```kotlin
val ga4 = FakeGoogleAnalytics().client()

ga4.sendEvents(
    clientId = ClientId.of("test-client"),
    events = listOf(GA4Event("purchase", mapOf("value" to "10.00")))
).successValue()

// Inspect stored events via storage to verify analytics were sent
```

## Chaos Testing

```kotlin
fakeGa4.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeGa4.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Events are stored in memory — inspect storage to verify event content and parameters
- No real data sent to Google Analytics
