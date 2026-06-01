---
license: http4k Commercial
module: http4k-connect-x402-fake
---

# http4k-connect-x402-fake Reference

Fake implementation of the X402 Facilitator for testing payment flows without real blockchain transactions.

## Construction

```kotlin
val fake = FakeX402Facilitator()

// Custom supported schemes
val fake = FakeX402Facilitator(
    supportedSchemes = listOf(
        SupportedKind(PaymentScheme.of("exact"), listOf(PaymentNetwork.of("base-sepolia")))
    )
)
```

## Usage as HttpHandler

```kotlin
val facilitator = X402Facilitator.Http(Uri.of(""), fake)

// All operations succeed by default
facilitator(Supported)                      // returns configured schemes
facilitator(Verify(payload, requirements))  // always valid, payer = "0xpayer"
facilitator(Settle(payload, requirements))  // always succeeds, tx = "0xtx"
```

## Running as Server

```kotlin
val server = FakeX402Facilitator().asServer(SunHttp(0)).start()
val facilitator = X402Facilitator.Http(Uri.of("http://localhost:${server.port()}"))
```

## Chaos Testing

`FakeX402Facilitator` extends `ChaoticHttpHandler`, supporting failure injection:

```kotlin
val fake = FakeX402Facilitator()
fake.returnStatus(INTERNAL_SERVER_ERROR)  // force errors
```

## Gotchas

- **Always succeeds**: Verify returns `isValid=true`, Settle returns `success=true`. Use chaos injection for failure scenarios.
- **Fixed responses**: Payer is always `0xpayer`, transaction hash is always `0xtx`, network is `base-sepolia`.
- **Requires client module**: The fake depends on `http4k-connect-x402` (client) for types and actions.
