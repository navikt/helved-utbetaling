---
license: Apache-2.0
module: http4k-connect-google-analytics-ua
---

# http4k-connect-google-analytics-ua Reference

Google Analytics Universal Analytics client — connect actions for GA UA Measurement Protocol.

## Client

```kotlin
val ua = GoogleAnalytics.Http(
    trackingId = TrackingId.of("UA-XXXXXXXXX-1"),
    http = JavaHttpClient()   // optional
)
```

## Send Hit

```kotlin
ua.sendHit(
    clientId = ClientId.of("user-device-id"),
    hitType = HitType.pageview,
    documentPath = "/home",
    documentTitle = "Home Page"
).successValue()
```

## Event Hit

```kotlin
ua.sendHit(
    clientId = ClientId.of("user-device-id"),
    hitType = HitType.event,
    eventCategory = "button",
    eventAction = "click",
    eventLabel = "signup-button",
    eventValue = 1
).successValue()
```

## Gotchas

- Universal Analytics (UA) is **deprecated** — Google no longer processes UA hits
- Use `connect-google-analytics-ga4` for new implementations
- `trackingId` format: `UA-XXXXXXXXX-Y` (property ID + view ID)
- No fake provided for UA — consider using GA4 with its fake for new test infrastructure
- UA Measurement Protocol uses GET or POST to `www.google-analytics.com/collect`
