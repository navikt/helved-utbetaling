---
license: http4k Commercial
module: http4k-ai-mcp-sdk
---

# http4k-ai-mcp-sdk Reference

MCP server implementation — expose tools, resources, and prompts over multiple transports.

## Server Setup

```kotlin
val tools = tools(
    ToolCapability(myTool, myToolHandler),
    ToolCapability(otherTool, otherHandler)
)

val resources = resources(
    ResourceCapability(myResource, myResourceHandler)
)

val prompts = prompts(
    PromptCapability(myPrompt, myPromptHandler)
)
```

## Creating MCP Server (Routing Function)

```kotlin
// Recommended: use mcp() routing function
val mcpApp = mcp(
    ServerMetaData("MyServer", "1.0.0"),
    NoMcpSecurity,
    tools, resources, prompts,
    path = "/mcp"  // default
)

mcpApp.asServer(Undertow(8080)).start()
```

## Custom Initialize Handler

The MCP handshake uses an `InitializeHandler` that can be customised for protocol version negotiation, capability filtering, or request-level auth:

```kotlin
// Default: SimpleInitializeHandler negotiates protocol version automatically
val protocol = McpProtocol(
    sessions,
    initializer(SimpleInitializeHandler(metadata)),
    tools = tools
)

// Custom handler — inspect the HTTP request or reject initialization
val customInitializer: InitializeHandler = { req: InitializeRequest ->
    if (req.protocolVersion < minVersion)
        InitializeResponse.Error("Unsupported protocol version")
    else
        InitializeResponse.Ok(
            serverInfo = metadata.entity,
            capabilities = metadata.capabilities,
            protocolVersion = req.protocolVersion
        )
}

val protocol = McpProtocol(
    sessions,
    initializer(customInitializer),
    tools = tools
)
```

`InitializeRequest` carries `clientInfo`, `capabilities`, `protocolVersion`, and the raw HTTP `connectRequest`.
`InitializeResponse` is a sealed interface with `Ok` and `Error` subtypes.

## Quick Server from Single or Multiple Capabilities

```kotlin
// Convert any ServerCapability directly to a server (optional name parameter)
val tool = Tool("greet", "Say hello") bind { Ok(Text("Hello!")) }
tool.asServer(JettyLoom(4001))                    // name defaults to "http4k-mcp"
tool.asServer(JettyLoom(4001), name = "my-server")

// Combine multiple capability instances and serve together
val myTools = tools(tool1, tool2)
val myResources = resources(resource1)
(myTools + myResources).asServer(JettyLoom(4001))

// Functional style — compose capabilities then serve
val tools = tools(Tool("time", "get the time") bind {
    ToolResponse.Ok(listOf(Content.Text(Instant.now().toString())))
})
val prompts = prompts(Prompt("p1", "desc") bind { PromptResponse.Ok(listOf(), "desc") })
(tools + prompts).asServer(JettyLoom(4001)).start()
```

## Transports (Low-Level)

```kotlin
// HTTP Streaming (SSE-based, recommended)
val mcpHandler = HttpStreamingMcp(tools, resources, prompts)

// HTTP Non-Streaming (request/response)
val mcpHandler = HttpNonStreamingMcp(tools, resources, prompts)

// JSON-RPC over HTTP
val mcpHandler = JsonRpcMcp(tools, resources, prompts)

// WebSocket
val mcpHandler = WebsocketMcp(tools, resources, prompts)

// Standard I/O (for subprocess-based MCP)
StdIoMcpSessions(tools, resources, prompts).start()
```

## Embedding in http4k Server

```kotlin
val app = routes(
    "/mcp" bind mcpHandler
)

app.asServer(Undertow(8080)).start()
```

## Security

```kotlin
val mcpHandler = HttpStreamingMcp(
    tools, resources, prompts,
    security = BearerAuthMcpSecurity { token -> token == "my-secret" }
)

// Available security types:
ApiKeyMcpSecurity { key -> key == "secret" }
BasicAuthMcpSecurity { creds -> creds.user == "admin" }
BearerAuthMcpSecurity { token -> validate(token) }
OAuthMcpSecurity(oauthHandler)
NoMcpSecurity  // default
```

## Observable Capabilities (Change Notifications)

```kotlin
val myTools = tools(ToolCapability(initialTool, handler))

// Dynamically add/remove tools — clients are notified
myTools.add(ToolCapability(newTool, newHandler))
myTools.remove(ToolName.of("old-tool"))
```

## Composing Capabilities

```kotlin
// Use capabilities() to pack multiple ServerCapability instances
val pack = capabilities(myTools, myResources, myPrompts)
val mcpHandler = HttpStreamingMcp(pack)

// Or use + operator directly on capability instances
val combined = myTools + myResources + myPrompts
```

## Logging from Server

```kotlin
val logger = Logger(sessions)
logger.log("Server started", LogLevel.info)
```

## OpenTelemetry Tracing

```kotlin
// Add MCP-level tracing (creates SERVER spans per JSON-RPC method)
// openTelemetry is the first parameter, spanModifiers is the second
val tracedMcp = McpFilters.OpenTelemetryTracing(openTelemetry)
    .then(mcpHandler)

// Uses GlobalOpenTelemetry by default
val tracedMcp = McpFilters.OpenTelemetryTracing().then(mcpHandler)
```

Each span is named after the JSON-RPC method plus the target name when available (e.g. `tools/call MyTool`, `prompts/get MyPrompt`) and includes attributes:
- `mcp.method.name` — the JSON-RPC method
- `mcp.session.id` — the MCP session ID
- `mcp.protocol.version` — the negotiated protocol version
- `jsonrpc.request.id` — the request ID (when present)

### Span Modifiers

Default span modifiers (`defaultMcpOtelSpanModifiers`) add method-specific attributes:

- **`CallToolSpanModifiers`** — adds `gen_ai.operation.name=execute_tool`, `gen_ai.tool.name`; sets ERROR status on tool errors (reads from `result.isError`)
- **`CompletionSpanModifiers`** — adds `gen_ai.operation.name=complete`, `mcp.completion.ref` (name or URI of the completion ref)
- **`GetPromptSpanModifiers`** — adds `gen_ai.operation.name=get_prompt`, `gen_ai.prompt.name`
- **`ReadResourceSpanModifiers`** — adds `gen_ai.operation.name=read_resource`, `mcp.resource.uri`

Custom modifiers implement `McpOpenTelemetrySpanModifiers` (provides `method`, `request()`, `response()` hooks).

### Detail Span Modifiers (Opt-In)

These modifiers capture request arguments and response content. They may contain sensitive data and are **not** included in `defaultMcpOtelSpanModifiers` — add them explicitly:

```kotlin
val tracedMcp = McpFilters.OpenTelemetryTracing(
    openTelemetry,
    spanModifiers = defaultMcpOtelSpanModifiers + listOf(
        CallToolDetailSpanModifiers,    // adds gen_ai.tool.call.arguments, gen_ai.tool.call.result
        GetPromptDetailSpanModifiers,   // adds gen_ai.prompt.arguments, gen_ai.prompt.result
        CompletionDetailSpanModifiers,  // adds completion arguments
        ReadResourceDetailSpanModifiers // adds resource content
    )
).then(mcpHandler)
```

### Transport Span Linking

When nested inside `PolyFilters.OpenTelemetryTracing()`, the MCP span automatically links to the transport span:

```kotlin
val poly = PolyFilters.OpenTelemetryTracing(openTelemetry).then(
    PolyHandler(http = myHttpHandler)
)
```

### Error Handling

- Exceptions: span status set to ERROR, `error.type` set to exception class name
- JSON-RPC errors: span status set to ERROR, `error.type` set to the error code

## Structured Tool Responses

```kotlin
// Return structured JSON alongside text content
val jsonData = McpJson.asJsonObject(mapOf("result" to 42, "message" to "success"))
ToolResponse.Ok(jsonData)  // creates both structured and text representation

// Text-only (traditional)
ToolResponse.Ok("plain text result")
ToolResponse.Ok(Content.Text("hello"), Content.Text("world"))
```

## RenderMcpApp Extra Capabilities

`RenderMcpApp` creates a combined Tool + Resource capability. Use `uiUri` (not `uri`) and `toolVisibility` (not `visibility`):

```kotlin
// Standard form — provide an McpAppHandler (renders HTML/UI content)
RenderMcpApp(
    name = "myApp",
    description = "My MCP app",
    uiUri = Uri.of("/app"),
    toolVisibility = listOf(McpAppVisibility.Desktop),
    extraCapabilities = listOf(myExtraTool)
) { req -> renderHtml(req) }

// Advanced form — provide a full ResourceHandler for custom resource serving
RenderMcpApp(
    name = "myApp",
    description = "My MCP app",
    uiUri = Uri.of("/app"),
    extraCapabilities = listOf(myExtraTool),
    toolVisibility = null,
    mimeType = McpApps.MIME_TYPE,
    resourceHandler = { req -> ResourceResponse.Ok(Resource.Content.Text("...", req.uri)) }
)
```

## Gotchas

- **`ServerTools`/`ServerResources`/`ServerPrompts`/`ServerInitializer` replaced**: Use `tools()`, `resources()`, `prompts()`, `initializer()` factory functions instead. The old class names no longer exist.
- **`compose()` removed**: Use `capabilities()` or the `+` operator to combine `ServerCapability` instances.
- **`McpProtocol` constructor changed**: `ServerMetaData` is no longer the first parameter. Pass `initializer(SimpleInitializeHandler(metadata))` as the second argument after `sessions`. The old constructor style `McpProtocol(metadata, sessions, ...)` no longer compiles.
- **Use `mcp()` not `mcpHttpStreaming()`**: `mcpHttpStreaming()` is deprecated — use `mcp()` as a direct replacement.
- `HttpStreamingMcp` is recommended for most use cases (supports progress, notifications)
- `StdIoMcpSessions` is for subprocess-based MCP servers (e.g., CLI tools)
- Each transport has matching connection class for lower-level control
- Security is applied per-transport, not per-capability
- `McpFilters.OpenTelemetryTracing()` requires `http4k-ops-opentelemetry` dependency alongside `http4k-ai-mcp-sdk`
