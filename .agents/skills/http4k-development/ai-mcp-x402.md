---
license: http4k Commercial
module: http4k-ai-mcp-x402
---

# http4k-ai-mcp-x402 Reference

X402 payment protocol integration for MCP servers — charge for tool calls and general MCP usage via a standardised X402 facilitator.

## Dependencies

Requires `http4k-ai-mcp-sdk` and `http4k-connect-x402`. For testing, add `http4k-connect-x402-fake` and `http4k-ai-mcp-testing`.

## Payment Check

```kotlin
sealed interface PaymentCheck {
    data class Required(val requirements: List<PaymentRequirements>) : PaymentCheck
    data object Free : PaymentCheck
}
```

Use `PaymentCheck` to decide per-request whether payment is needed.

## X402ToolFilter (Tool-Level Payments)

Wraps individual tools with payment verification and settlement. Returns structured `PaymentRequired` errors when payment is missing or invalid, and includes settlement details in the response meta on success.

```kotlin
val requirements = PaymentRequirements(
    scheme = PaymentScheme.of("exact"),
    network = PaymentNetwork.of("base-sepolia"),
    asset = AssetAddress.of("0xUSDC"),
    amount = PaymentAmount.of("100"),
    payTo = WalletAddress.of("0xmerchant"),
    maxTimeoutSeconds = 60
)

val facilitator = X402Facilitator.Http(Uri.of("https://facilitator.example.com"), http)

val paidTool = X402ToolFilter(facilitator) { PaymentCheck.Required(listOf(requirements)) }
    .then(Tool("premium_data", "get premium data") bind { Ok(listOf(Content.Text("Here is your data!"))) })

val server = mcp(
    ServerMetaData(McpEntity.of("paid-server"), Version.of("1.0.0")),
    NoMcpSecurity,
    paidTool
)
```

## X402McpFilter (Protocol-Level Payments)

Operates at the MCP protocol level via `McpFilters`. Throws `McpException` with code 402 on payment failure instead of returning structured tool errors.

```kotlin
val filter = McpFilters.X402PaymentRequired(facilitator) { request: McpRequest ->
    PaymentCheck.Required(listOf(requirements))
}
```

## Meta Keys for Payment Data

Payment data flows through MCP `_meta` fields using the lens system:

```kotlin
// Create lenses
val paymentLens = MetaKey.x402PaymentPayload().toLens()
val settlementLens = MetaKey.x402Settled().toLens()

// Inject payment into tool request meta
val request = ToolRequest(meta = Meta(paymentLens of payload))

// Extract settlement from tool response meta
val settled: SettledResponse? = settlementLens(response.meta)
```

### Meta Field Keys

| Key | Type | Direction |
|-----|------|-----------|
| `x402/payment` | `PaymentPayload` | Client → Server (in tool call `_meta`) |
| `x402/payment-response` | `SettledResponse` | Server → Client (in tool response `_meta`) |

## Payment Flow (Tool-Level)

1. Client calls tool without payment → `ToolResponse.Error` with `PaymentRequired` in `structuredContent`
2. Client extracts requirements from error, signs payment, retries with payment in `_meta`
3. Server matches payment scheme/network against requirements
4. Server verifies via facilitator, executes tool, settles via facilitator
5. Server returns `ToolResponse.Ok` with `SettledResponse` in response `_meta`

## Error Responses

`X402ToolFilter` returns `ToolResponse.Error` with:
- `content`: JSON string of `PaymentRequired`
- `structuredContent`: JSON object of `PaymentRequired` (for programmatic access)

`X402McpFilter` throws `McpException(ErrorMessage(402, message))`.

## Testing

```kotlin
val fake = FakeX402Facilitator()

val paidTool = X402ToolFilter(fake.client()) { PaymentCheck.Required(listOf(requirements)) }
    .then(Tool("data", "get data") bind { Ok(listOf(Content.Text("result"))) })

val server = mcp(metadata, NoMcpSecurity, paidTool)

server.testMcpClient(Request(POST, "/mcp")).use { client ->
    // Without payment — returns error with PaymentRequired
    val error = client.tools().call(ToolName.of("data"), ToolRequest())

    // With payment — succeeds
    val paymentLens = MetaKey.x402PaymentPayload().toLens()
    val result = client.tools().call(
        ToolName.of("data"),
        ToolRequest(meta = Meta(paymentLens of payment))
    )
}
```

## Gotchas

- **Scheme/network matching**: Payment is matched to requirements by `scheme` + `network` pair. If no requirement matches the payment's scheme/network, a "Unsupported payment scheme/network" error is returned.
- **Settlement is automatic**: After successful verification, settlement happens immediately. If settlement fails, the tool response is suppressed and a payment error is returned instead.
- **ToolFilter vs McpFilter**: `X402ToolFilter` returns structured `ToolResponse.Error` (tool-level). `McpFilters.X402PaymentRequired` throws `McpException` (protocol-level). Use `X402ToolFilter` for per-tool payment gating.
- **Meta lens creation**: Use `MetaKey.x402PaymentPayload()` and `MetaKey.x402Settled()` — these return specs that need `.toLens()` before use.
- **Requires Moshi**: Payment serialization uses `X402Moshi`. Ensure `http4k-format-moshi` is on the classpath.
