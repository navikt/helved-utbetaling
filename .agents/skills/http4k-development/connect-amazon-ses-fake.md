---
license: Apache-2.0
module: http4k-connect-amazon-ses-fake
---

# http4k-connect-amazon-ses-fake Reference

In-memory fake SES server for testing email sending.

## Setup

```kotlin
val fakeSes = FakeSES()
val client = fakeSes.client()
```

## Custom Configuration

```kotlin
val fakeSes = FakeSES(
    emails = Storage.InMemory(),
    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
)
```

## Test Pattern

```kotlin
val ses = FakeSES().client()

ses.sendEmail(
    source = EmailAddress.of("from@example.com"),
    destination = Destination(toAddresses = listOf(EmailAddress.of("to@example.com"))),
    message = Message(
        subject = Content("Test"),
        body = Body(text = Content("Body"))
    )
).successValue()

// Inspect sent emails via storage
```

## Chaos Testing

```kotlin
fakeSes.returnStatus(Status.SERVICE_UNAVAILABLE)
fakeSes.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Sent emails are stored in memory — inspect storage to assert email content in tests
- No real email delivery occurs
