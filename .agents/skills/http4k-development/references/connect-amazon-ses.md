---
license: Apache-2.0
module: http4k-connect-amazon-ses
---

# http4k-connect-amazon-ses Reference

SES client — connect actions for Amazon Simple Email Service.

## Client

```kotlin
val ses = SES.Http(
    region = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient()   // optional
)
```

## Send Email

```kotlin
ses.sendEmail(
    source = EmailAddress.of("sender@example.com"),
    destination = Destination(
        toAddresses = listOf(EmailAddress.of("recipient@example.com")),
        ccAddresses = listOf(EmailAddress.of("cc@example.com"))
    ),
    message = Message(
        subject = Content("Subject line"),
        body = Body(
            text = Content("Plain text body"),
            html = Content("<h1>HTML body</h1>")
        )
    )
).successValue()
```

## Gotchas

- Uses XML/query protocol (not JSON) — underlying HTTP requests use form-encoded parameters
- Sender address must be verified in SES (or domain verified) unless out of sandbox mode
- In sandbox mode, recipient addresses must also be verified
- SES v2 is a separate module/API — this client targets SES v1 (Classic)
- `Content` objects support `charset` parameter (default UTF-8)
