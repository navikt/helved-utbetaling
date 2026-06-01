---
license: Apache-2.0
module: http4k-webhook
---

# http4k-webhook Reference

Webhook payload signing and verification using HMAC SHA256.

## Signing (Client Filter)

```kotlin
val signingSecret = HmacSha256SigningSecret.of("my-secret")
val signer = HmacSha256.Signer(signingSecret)

val client = ClientFilters.SignWebhookPayload(signer, Jackson)
    .then(JavaHttpClient())
```

Adds `webhook-id`, `webhook-timestamp`, and `webhook-signature` headers to outgoing requests.

## Verification (Server Filter)

```kotlin
val verifier = HmacSha256.Verifier(signingSecret)

val app = ServerFilters.VerifyWebhookSignature(verifier)
    .then { Response(OK) }
// Returns 401 UNAUTHORIZED if signature invalid
```

Custom failure response:

```kotlin
ServerFilters.VerifyWebhookSignature(
    verifier,
    onFailure = { Response(FORBIDDEN) }
)
```

## Manual Signing/Verification

```kotlin
val signer: WebhookSigner = HmacSha256.Signer(signingSecret)
val signature: WebhookSignature = signer(id, timestamp, body)

val verifier: WebhookSignatureVerifier = HmacSha256.Verifier(signingSecret)
val valid: Boolean = verifier(id, timestamp, signature, body)
```

## Webhook Payload

```kotlin
data class WebhookPayload<T>(
    val type: EventType,
    val timestamp: WebhookTimestamp,
    val data: T
)
```

## Headers

```kotlin
Header.WEBHOOK_ID(request)          // webhook-id
Header.WEBHOOK_SIGNATURE(request)   // webhook-signature
Header.WEBHOOK_TIMESTAMP(request)   // webhook-timestamp
```

## Signature Format

Signature content: `"$id.${timestamp.asInstant()}.$bodyContent"`
Encoded as: `v1_<base64(hmacSha256(content))>`

## Gotchas

- Signature verification ignores lens failures (returns `false` rather than throwing)
- `SignWebhookPayload` uses AutoMarshalling to deserialize the body as `WebhookPayload`
- Default `idGenerator` creates a new `WebhookId` per request if not provided
