---
license: http4k Commercial
module: http4k-connect-x402
---

# http4k-connect-x402 Reference

HTTP 402 Payment Required protocol support — payment-gated API access using cryptocurrency.

## Flow Overview

1. Client requests protected resource without payment
2. Server responds 402 with payment requirements in `X-PAYMENT-REQUIRED` header
3. Client signs requirements via `X402Signer` and retries with `X-PAYMENT` header
4. Server verifies and settles via `X402Facilitator`
5. Server responds 200 with settlement details in `X-PAYMENT-RESPONSE` header

## Client Setup

```kotlin
// Create facilitator client
val facilitator = X402Facilitator.Http(
    Uri.of("https://facilitator.example.com"),
    JavaHttpClient()  // optional, defaults to JavaHttpClient
)

// Create signer (you provide the signing implementation, e.g. web3j)
val signer = X402Signer { requirements ->
    // Sign requirements and return PaymentPayload
    Success(PaymentPayload(
        x402Version = 2,
        scheme = PaymentScheme.of("exact"),
        network = PaymentNetwork.of("base-sepolia"),
        payload = mapOf("signature" to "0xabc"),
        resource = Uri.of("https://api.example.com/data"),
        description = "API access"
    ))
}

// Apply client filter — automatically handles 402 → sign → retry
val client = ClientFilters.X402PaymentRequired(signer)
    .then(JavaHttpClient())
```

## Server Setup

```kotlin
val requirements = PaymentRequirements(
    scheme = PaymentScheme.of("exact"),
    network = PaymentNetwork.of("base-sepolia"),
    asset = AssetAddress.of("0xUSDC"),
    amount = PaymentAmount.of("100"),
    payTo = WalletAddress.of("0xmerchant"),
    maxTimeoutSeconds = 30
)

// Server filter — verifies and settles payments
val app = ServerFilters.X402PaymentRequired(
    facilitator = facilitator,
    requirements = { request -> listOf(requirements) }
).then(myHandler)

// Or use as Security provider
val security = X402Security(
    requirements = { listOf(requirements) },
    facilitator = facilitator
)
```

## Facilitator Actions

```kotlin
// Check supported payment schemes
val supported = facilitator(Supported)

// Verify a payment signature
val verified = facilitator(Verify(payload, requirements))

// Settle (execute) a payment
val settled = facilitator(Settle(payload, requirements))
```

## Value Types

```kotlin
PaymentScheme.of("exact")
PaymentNetwork.of("base-sepolia")
WalletAddress.of("0xmerchant")
AssetAddress.of("0xUSDC")
PaymentAmount.of("100")
TransactionHash.of("0xtx123")
```

## Testing with Fake

```kotlin
// FakeX402Facilitator always succeeds (verifies and settles)
val fake = FakeX402Facilitator()

// Use as HttpHandler directly
val facilitator = X402Facilitator.Http(Uri.of(""), fake)

// Or start as real server
val server = FakeX402Facilitator().asServer(SunHttp(0)).start()

// Customize supported schemes
val fake = FakeX402Facilitator(
    supportedSchemes = listOf(
        SupportedKind(PaymentScheme.of("exact"), listOf(PaymentNetwork.of("base-sepolia")))
    )
)
```

## End-to-End Test Pattern

```kotlin
val fake = FakeX402Facilitator()
val facilitator = X402Facilitator.Http(Uri.of(""), fake)

val signer = X402Signer { Success(payload) }

val server = ServerFilters.X402PaymentRequired(
    facilitator, { listOf(requirements) }
).then { Response(OK).body("paid content") }

val client = ClientFilters.X402PaymentRequired(signer).then(server)

val response = client(Request(GET, "/resource"))
// response.status == OK, body == "paid content"
```

## Headers

| Header | Direction | Content |
|--------|-----------|---------|
| `X-PAYMENT-REQUIRED` | Server → Client | Base64-encoded `PaymentRequired` |
| `X-PAYMENT` | Client → Server | Base64-encoded `PaymentPayload` |
| `X-PAYMENT-RESPONSE` | Server → Client | Base64-encoded `Settled` |

## Gotchas

- **Bring your own signer**: The module provides the protocol, not the crypto. Use an OSS library like web3j for actual signing.
- **Server matches by scheme/network**: `ServerFilters.X402PaymentRequired` matches the payment's `scheme` + `network` against the requirements list. If no match is found, a 402 error is returned.
- **All value types are NonBlankString**: `PaymentScheme.of("")` throws — values must be non-blank.
- **Nullable response fields**: `VerifyResponse.payer` is only populated when `isValid == true`. `SettleResponse` fields (transaction, network, payer) only populated when `success == true`.
- **Client filter retry**: On 402, the client filter signs and retries once. If the retry also returns 402, that response is returned as-is (no infinite retry).
- **Uses Moshi**: Serialization uses `X402Moshi` (Kotshi-backed). The `http4k-format-moshi` dependency is required.
