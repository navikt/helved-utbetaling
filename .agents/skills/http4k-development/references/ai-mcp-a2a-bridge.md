---
module: http4k-ai-mcp-a2a-bridge
license: http4k Commercial
---

# http4k-ai-mcp-a2a-bridge Reference

Bridges an A2A (Agent-to-Agent) agent as a set of MCP tools, so any MCP client can interact with an A2A agent without speaking the A2A protocol directly. Requires `http4k-ai-a2a-client` and `http4k-ai-mcp-sdk`.

## What It Exposes

`mcpA2aBridge` always exposes exactly four MCP tools:

| Tool | Description |
|------|-------------|
| `send_message` | Send a message to the A2A agent |
| `get_task` | Get the current state of a task |
| `cancel_task` | Cancel a running task |
| `list_tasks` | List tasks, optionally filtered by context or status |

The `send_message` tool description is auto-populated from the agent card (name, description, skills, and examples), so MCP clients get a rich tool description without extra configuration.

## Quick Start: Bridge a Fixed A2A Client

```kotlin
val a2aClient = HttpA2AClient(Uri.of("https://my-agent.example.com"))

// mcpA2aBridge fetches the agent card eagerly — fails fast if unreachable
val bridge: ServerCapability = mcpA2aBridge(a2aClient)

// Compose into any http4k MCP server
val server = mcp(
    identity = ServerMetaData(McpEntity.of("my-mcp-server"), Version.of("1.0.0")),
    security = NoMcpSecurity,
    bridge
)
```

## Per-Call Auth: Forward Inbound Headers

When each MCP tool call should use the caller's own credentials:

```kotlin
// clientFor is invoked per tool call with the inbound MCP request
val bridge: ServerCapability = mcpA2aBridge { inboundMcpRequest ->
    val auth = Header.optional("Authorization")(inboundMcpRequest)
    HttpA2AClient(
        baseUri = Uri.of("https://my-agent.example.com"),
        http = ClientFilters.SetHeader("Authorization", auth ?: "").then(JavaHttpClient())
    )
}
```

Or use the built-in `authLens` overload which handles `Authorization` forwarding automatically:

```kotlin
val bridge: ServerCapability = mcpA2aBridge(
    baseUri = Uri.of("https://my-agent.example.com"),
    authLens = Header.optional("Authorization")
)
```

## Complete MCP Server (One-Liner)

`mcpA2aBridgeServer` wires the entire stack — MCP server + A2A bridge — forwarding the inbound `Authorization` header to the A2A agent:

```kotlin
val server = mcpA2aBridgeServer(
    identity = ServerMetaData(McpEntity.of("bridge-server"), Version.of("1.0.0")),
    baseUri = Uri.of("https://my-agent.example.com"),
    security = BearerAuthMcpSecurity { it == "my-mcp-token" },
    path = "/mcp"   // default
)

server.asServer(Helidon(8080)).start()
```

For other passthrough schemes (e.g. `X-Api-Key`), use `mcpA2aBridge` with a custom `authLens` and compose with `mcp(...)` manually.

## Multi-Tenant: Custom `clientFor` with Tenant

```kotlin
val tenantLens = Header.required("X-Tenant-Id")

val bridge = mcpA2aBridge { inboundRequest ->
    val tenant = Tenant.of(tenantLens(inboundRequest))
    HttpA2AClient(
        baseUri = Uri.of("https://my-agent.example.com"),
        tenant = tenant,
        http = JavaHttpClient(responseBodyMode = BodyMode.Stream)
    )
}
```

## Deterministic Testing

Pass a seeded `Random` to get reproducible message IDs in tests:

```kotlin
val bridge = mcpA2aBridge(myA2AClient, random = Random(42))
```

## Testing with a Fake A2A Server

```kotlin
val card = AgentCard(name = "Test", version = Version.of("1.0.0"), description = "test agent")

val fakeA2A = a2aJsonRpc(
    agentCard = card,
    tasks = TaskStorage.InMemory(),
    pushNotifications = PushNotificationConfigStorage.InMemory()
) { request ->
    Message(MessageId.of("resp"), ROLE_AGENT, listOf(Part.Text("pong")))
}

val bridge = mcpA2aBridge({ fakeA2A.testA2AJsonRpcClient() }, random = Random(0))
```

## Gotchas

- **Agent card fetched at construction**: `mcpA2aBridge` calls `agentCard()` immediately. If the A2A agent is unreachable, an `IllegalStateException` is thrown during bridge creation — not on the first tool call.
- **`clientFor` called per tool call**: In the `clientFor` overload, a new `A2AClient` is constructed for every tool invocation. Keep construction lightweight or cache the underlying `HttpHandler`.
- **Agent card fetch uses no auth**: When using the `authLens` / `baseUri` overload, the agent card is fetched with no `Authorization` header. The agent's `/.well-known/agent-card.json` must be public.
- **`send_message` description reflects card at startup**: The tool description is generated once from the agent card when `mcpA2aBridge` is called. Card changes at the A2A agent are not reflected until the bridge is recreated.
- **Streaming A2A responses**: For `ResponseStream` A2A responses, the bridge collects the stream to a list and returns the last `Message` and/or last `Task` — no SSE streaming to the MCP client.
