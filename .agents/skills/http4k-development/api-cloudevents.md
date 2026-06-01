---
license: Apache-2.0
module: http4k-api-cloudevents
---

# http4k-api-cloudevents Reference

CNCF CloudEvents support for http4k. Read and write CloudEvents from HTTP messages with automatic structured/binary encoding detection.

## CloudEvent Lens

```kotlin
val lens = Body.cloudEvent().toLens()

// Write a CloudEvent into a request
val event = CloudEventBuilder.v1()
    .withId("123")
    .withType("order.created")
    .withSource(Uri.of("https://shop.example.com"))
    .withData(orderData)
    .build()

val request = Request(POST, "/events").with(lens of event)

// Read a CloudEvent from a request
val received: CloudEvent = lens(request)
```

## Building CloudEvents

```kotlin
val event = CloudEventBuilder.v1()
    .withId("event-123")
    .withType("com.example.order.created")
    .withSource(Uri.of("https://orders.example.com"))   // http4k Uri extension
    .withDataContentType(APPLICATION_JSON)                // http4k ContentType extension
    .withDataSchema(Uri.of("https://schema.example.com/order"))
    .withSubject("order-456")
    .withTime(OffsetDateTime.now())
    .withData(data)
    .build()
```

## Structured vs Binary Encoding

The lens auto-detects the encoding mode when reading:

- **Structured**: `Content-Type: application/cloudevents+json` — entire event (metadata + data) in the body
- **Binary**: `ce-specversion`, `ce-id`, `ce-type`, `ce-source` headers — metadata in headers, data in body

When writing, the lens uses structured encoding by default (based on the content type passed to `Body.cloudEvent()`).

## Jackson Integration

```kotlin
// Register the Jackson CloudEvents format
EventFormatProvider.getInstance().registerFormat(Jackson.cloudEventsFormat())

// Create a typed data lens to extract data from a CloudEvent
val dataLens = Jackson.cloudEventDataLens<OrderEvent>()

val cloudEvent = lens(request)
val orderEvent: OrderEvent = dataLens(cloudEvent)
```

## Custom Content Type

```kotlin
// Use a custom content type for non-JSON formats
val csvLens = Body.cloudEvent(ContentType("application/cloudevents+csv")).toLens()
```

## Gotchas

- **Register format first**: Call `EventFormatProvider.getInstance().registerFormat(Jackson.cloudEventsFormat())` before using the lens with structured encoding.
- **Auto-detection**: The lens tries structured encoding first (by content-type), then binary (by `ce-specversion` header). Throws if neither is found.
- **http4k Uri extensions**: Builder methods `withSource(Uri)` and `withDataSchema(Uri)` accept http4k `Uri` directly.
- **http4k ContentType extension**: `withDataContentType(ContentType)` accepts http4k `ContentType` directly.
- **Jackson dependency**: `cloudEventsFormat()` and `cloudEventDataLens<T>()` are extensions on `ConfigurableJackson`.
