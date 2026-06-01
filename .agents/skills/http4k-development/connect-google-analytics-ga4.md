---
license: Apache-2.0
module: http4k-connect-google-analytics-ga4
---

# http4k-connect-google-analytics-ga4 Reference

Google Analytics GA4 client — connect actions for Google Analytics 4 Measurement Protocol.

## Client

```kotlin
val ga4 = GoogleAnalytics.Http(
    measurementId = MeasurementId.of("G-XXXXXXXXXX"),
    apiSecret = ApiSecret.of(System.getenv("GA4_API_SECRET")),
    http = JavaHttpClient()   // optional
)
```

## Send Events

```kotlin
ga4.sendEvents(
    clientId = ClientId.of("user-device-id"),
    events = listOf(
        GA4Event(
            name = "purchase",
            params = mapOf(
                "currency" to "USD",
                "value" to "99.99",
                "transaction_id" to "T_12345"
            )
        )
    )
).successValue()
```

## Page View

```kotlin
ga4.sendEvents(
    clientId = ClientId.of("user-device-id"),
    events = listOf(
        GA4Event(
            name = "page_view",
            params = mapOf(
                "page_location" to "https://example.com/home",
                "page_title" to "Home"
            )
        )
    )
).successValue()
```

## Gotchas

- GA4 Measurement Protocol uses `apiSecret` (different from service account credentials)
- `measurementId` is the stream ID starting with `G-`
- `clientId` should be a stable device/user identifier (UUID recommended)
- Events are batched — up to 25 events per request
- GA4 Measurement Protocol data may take 24-48 hours to appear in reports
- Validation endpoint available at `www.google-analytics.com/debug/mp/collect` for testing event structure
