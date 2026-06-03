---
license: http4k Commercial
module: http4k-connect-mpp
---

# http4k-connect-mpp Reference

Machine Payments Protocol (MPP) — HTTP-level payment authentication using a challenge/credential/receipt flow. Supports both server-side enforcement and client-side automatic handling.

## Core Interfaces

```kotlin
// Server side: verify a payment credential and return a receipt
fun interface MppVerifier {
    fun verify(credential: Credential): Result<Receipt, RemoteFailure>
}

// Client side: sign a challenge to produce a credential
fun interface MppSigner {
    fun sign(challenge: Challenge): Result<Credential, String>
}
```

## Server Filter

```kotlin
// Protect a route — unauthenticated requests get a 402 with a challenge
val protectedApp = ServerFilters.MppPaymentRequired(
    verifier = myVerifier,
    challengeFor = { request -> Challenge(
        id = ChallengeId.of("challenge-1"),
        realm = Realm.of("my-service"),
        method = PaymentMethod.of("lightning"),
        intent = PaymentIntent.of("payment"),
        request = ChargeRequest(
            amount = PaymentAmount.of("1000"),
            currency = Currency.of("SATS")
        )
    )}
).then(myHandler)
```

Without credential → 402 with:
- `WWW-Authenticate: Payment <base64url-challenge>`
- `Cache-Control: no-store`
- Body: `MppProblem.paymentRequired` JSON

With valid credential → 200 with `Payment-Receipt: <base64url-receipt>` header.

With invalid credential → 402 with `MppProblem.verificationFailed` JSON.

## Client Filter

```kotlin
// Automatically handle 402 responses — sign the challenge and retry
val client = ClientFilters.MppPaymentRequired(signer = mySigner).then(httpClient)
```

On 402 response:
1. Extracts challenge from `WWW-Authenticate` header
2. Calls signer → on success, retries with `Authorization: Payment <base64url-credential>`
3. On signing failure, returns the original 402 response unchanged

## Security Wrapper

```kotlin
// Use with MCP servers or other Security-aware frameworks
val security = MppSecurity(
    challengeFor = { request -> Challenge(...) },
    verifier = myVerifier
)
```

## Key Models

```kotlin
// Challenge: issued by server, describes what payment is required
Challenge(
    id = ChallengeId.of("uuid"),
    realm = Realm.of("service-name"),
    method = PaymentMethod.of("lightning"),
    intent = PaymentIntent.of("payment"),
    request = ChargeRequest(
        amount = PaymentAmount.of("1000"),
        currency = Currency.of("SATS"),
        recipient = Recipient.of("payee"),
        description = "Premium access"
    ),
    expires = Instant.now().plusSeconds(300)
)

// Credential: produced by client after signing a challenge
Credential(
    challenge = challenge,
    source = PaymentSource.of("wallet-id"),
    payload = mapOf("proof" to "signature-bytes")
)

// Receipt: returned on successful payment verification
Receipt(
    status = ReceiptStatus.success,
    method = PaymentMethod.of("lightning"),
    timestamp = Instant.now(),
    challengeId = ChallengeId.of("uuid"),
    reference = PaymentReference.of("payment-hash")
)
```

## Problem Types (Standard 402 Error Bodies)

```kotlin
MppProblem.paymentRequired      // No credential provided
MppProblem.verificationFailed   // Credential verification failed
MppProblem.malformedCredential  // Credential could not be parsed
MppProblem.invalidChallenge     // Challenge is invalid or expired
```

All have `status = 402`.

## Header Lenses

```kotlin
// Read challenge from WWW-Authenticate header
val challenge: Challenge = mppChallengeLens(response)

// Read credential from Authorization header (nullable)
val credential: Credential? = mppCredentialLens(request)

// Read receipt from Payment-Receipt header
val receipt: Receipt = mppReceiptLens(response)
```

Headers use `Payment ` prefix + base64url encoding (no padding).

## ChargeRequest URL Encoding

```kotlin
// Encode for URL params / query strings
val encoded: String = chargeRequest.encodeToRequestParam()

// Decode back
val decoded: ChargeRequest = encoded.decodeToChargeRequest()
```

Uses base64url (no `+` or `/` characters, no padding).

## Gotchas

- **All value types use factory methods**: `ChallengeId.of(...)`, `Realm.of(...)`, etc. — constructor is private.
- **Headers are base64url-encoded with `Payment ` prefix**: The WWW-Authenticate and Authorization headers use `Payment <base64url>`, not `Bearer`.
- **Retry is exactly once**: `ClientFilters.MppPaymentRequired` retries once after signing; if the retry also returns 402, it returns that response without further attempts.
- **Signing failure returns original 402**: If `MppSigner.sign()` returns `Failure`, the client filter returns the original 402 response, not an exception.
- **`Cache-Control: no-store` on 402**: Server always sets this on payment-required responses to prevent caching of challenges.
- **JSON serialization via MppMoshi**: Use `MppMoshi` for serializing/deserializing MPP models — it is pre-configured with all required adapters.
