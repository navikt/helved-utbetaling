---
license: http4k Commercial
module: http4k-ai-mcp-mpp
---

# http4k-ai-mcp-mpp Reference

MCP-level Machine Payments Protocol integration — add payment authentication to MCP tools and servers. Two levels of enforcement: protocol-level (entire MCP session) and tool-level (per tool).

Depends on `http4k-ai-mcp-sdk` and `http4k-connect-mpp`.

## MCP Server with Payment Extension

Declare supported payment methods on the server by adding `MppPayments` to `ServerMetaData`:

```kotlin
val metadata = ServerMetaData("MyServer", "1.0.0")
    .withExtensions(MppPayments(
        methods = listOf(PaymentMethod.of("lightning"), PaymentMethod.of("stripe")),
        intents = listOf(PaymentIntent.of("payment"), PaymentIntent.of("subscription"))
    ))

val mcpApp = mcp(metadata, NoMcpSecurity, tools, resources, prompts)
```

`MppPayments` implements `McpExtension` — appears in `ServerCapabilities.extensions["payment"]`.

## Tool-Level Payment Protection (MppToolFilter)

Wrap individual tools with `MppToolFilter` to require payment per tool call:

```kotlin
val premiumTool = Tool("premium_data", "Expensive operation") bind MppToolFilter(
    verifier = myVerifier,
    check = { request: ToolRequest ->
        MppPaymentCheck.Required(listOf(
            Challenge(
                id = ChallengeId.of(UUID.randomUUID().toString()),
                realm = Realm.of("my-service"),
                method = PaymentMethod.of("lightning"),
                intent = PaymentIntent.of("payment"),
                request = ChargeRequest(PaymentAmount.of("1000"), Currency.of("SATS"))
            )
        ))
    }
).then(actualToolHandler)
```

Without credential in `_meta` → `ToolResponse.Error` with message "Payment required" and `structuredContent` containing challenges JSON.

With valid credential → `ToolResponse.Ok` with receipt attached to response meta.

With invalid credential → `ToolResponse.Error` with failure message and challenges.

When `check` returns `MppPaymentCheck.Free` → passes through to handler.

## Protocol-Level Payment Protection (McpFilters.MppPaymentRequired)

Apply payment check to all MCP requests at the protocol level:

```kotlin
val protectedMcp = McpFilters.MppPaymentRequired(
    verifier = myVerifier,
    check = { request: McpRequest ->
        MppPaymentCheck.Required(listOf(myChallenge))
    }
).then(mcpHandler)
```

Without credential → throws `McpException` with code `-32042` (PAYMENT_REQUIRED_CODE), message "Payment required", error data contains challenges.

Verification failure → throws `McpException` with code `-32043` (VERIFICATION_FAILED_CODE), error data contains challenges.

## Meta Lenses (Accessing Credential/Receipt in MCP Requests)

```kotlin
// Read credential from _meta in incoming ToolRequest
val credentialLens = MetaKey.mppCredential().toLens()
val credential: Credential? = credentialLens(request.meta)

// Read receipt from _meta in outgoing ToolResponse
val receiptLens = MetaKey.mppReceipt().toLens()
val receipt: Receipt? = receiptLens(response.meta)
```

Meta key for credential: `"org.paymentauth/credential"`
Meta key for receipt: `"org.paymentauth/receipt"`

Both lenses return `null` when the key is absent.

## MppPaymentCheck

Sealed interface returned by the `check` lambda in both filters:

```kotlin
// Require payment — provide challenges the client should solve
MppPaymentCheck.Required(challenges = listOf(myChallenge))

// No payment required — pass through to handler
MppPaymentCheck.Free
```

## Complete Example

```kotlin
val challenges = listOf(
    Challenge(
        id = ChallengeId.of("challenge-1"),
        realm = Realm.of("my-service"),
        method = PaymentMethod.of("lightning"),
        intent = PaymentIntent.of("payment"),
        request = ChargeRequest(PaymentAmount.of("1000"), Currency.of("SATS"))
    )
)

val verifier = MppVerifier { credential ->
    if (credential.payload["proof"] == "valid") {
        Success(Receipt(ReceiptStatus.success, credential.challenge.method,
            Instant.now(), credential.challenge.id))
    } else {
        Failure(RemoteFailure(Method.POST, Uri.of("/verify"), Status.UNAUTHORIZED))
    }
}

val premiumTool = Tool("premium_data", "Requires payment") bind
    MppToolFilter(verifier) { MppPaymentCheck.Required(challenges) }
        .then { ToolResponse.Ok("Premium data!") }

val freeTool = Tool("free_data", "Always free") bind { ToolResponse.Ok("Free!") }

val metadata = ServerMetaData("MyServer", "1.0.0")
    .withExtensions(MppPayments(
        methods = listOf(PaymentMethod.of("lightning")),
        intents = listOf(PaymentIntent.of("payment"))
    ))

val server = mcp(metadata, NoMcpSecurity, ServerTools(
    listOf(ToolCapability(premiumTool), ToolCapability(freeTool))
))
```

## Gotchas

- **Two levels of protection**: `MppToolFilter` is per-tool (returns `ToolResponse.Error`); `McpFilters.MppPaymentRequired` is protocol-level (throws `McpException`). Use tool-level for fine-grained control, protocol-level for blanket enforcement.
- **`MppPayments` extension must be on `ServerMetaData`**: Clients need capability negotiation to know payment is supported. Add via `.withExtensions(MppPayments(...))`.
- **Credential is in `_meta` field of the tool request**: MCP clients must put the credential at `_meta["org.paymentauth/credential"]`. Raw HTTP clients use `Authorization: Payment <base64url>`.
- **Receipt is attached to response meta**: After successful verification, `MppToolFilter` attaches the receipt to `_meta["org.paymentauth/receipt"]` in the `ToolResponse.Ok`.
- **`check` lambda runs before credential extraction**: If `check` returns `Free`, no credential is needed or read at all — the tool handler runs unconditionally.
