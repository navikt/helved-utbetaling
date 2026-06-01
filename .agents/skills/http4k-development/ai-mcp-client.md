---
license: http4k Commercial
module: http4k-ai-mcp-client
---

# http4k-ai-mcp-client Reference

MCP client — connect to MCP servers and call tools, resources, and prompts.

## Creating a Client

```kotlin
// HTTP Streaming (SSE-based, recommended)
val client = HttpStreamingMcpClient(
    baseUri = Uri.of("http://localhost:8080/mcp"),
    name = McpEntity.of("my-client"),       // optional, defaults to "http4k-mcp-client"
    version = Version.of("1.0.0"),          // optional, defaults to "0.0.0"
    http = JavaHttpClient()                 // optional
)

// Minimal — URI is the only required parameter
val client = HttpStreamingMcpClient(Uri.of("http://localhost:8080/mcp"))

// HTTP Non-Streaming
val client = HttpNonStreamingMcpClient(Uri.of("http://localhost:8080/mcp"))

// WebSocket
val client = WebsocketMcpClient(Request(GET, "ws://localhost:8080/mcp"))

// SSE
val client = SseMcpClient(Request(GET, "http://localhost:8080/mcp"))
```

## Connecting

```kotlin
client.use { mcp ->
    val caps: McpInitialize.Response.Result = mcp.start().getOrThrow()
    println("Connected. Tools: ${caps.capabilities.tools != null}")

    // ... use the client
}
// AutoCloseable — client.stop() called on exit
```

`start()` returns `McpResult<McpInitialize.Response.Result>` — the inner type changed from `McpInitialize.Response` to `McpInitialize.Response.Result`.

## Tools

```kotlin
val tools = mcp.tools()

// List available tools
val toolList: List<McpTool> = tools.list().getOrThrow()

// Call a tool
val result: ToolResponse = tools.call(
    ToolName.of("calculator"),
    ToolRequest(mapOf("operation" to "add", "a" to 1, "b" to 2))
).getOrThrow()

// React to tool list changes
tools.onChange { println("Tool list updated") }
```

## Resources

```kotlin
val resources = mcp.resources()

val list: List<McpResource> = resources.list().getOrThrow()
val templates: List<McpResource> = resources.listTemplates().getOrThrow()

val content: ResourceResponse = resources.read(
    ResourceRequest(Uri.of("file:///data/config.json"))
).getOrThrow()

// ResourceResponse is a sealed interface — pattern-match on the result
when (content) {
    is ResourceResponse.Ok -> content.list.forEach { println(it) }
    is ResourceResponse.Error -> println("Error: ${content.message}")
}

resources.subscribe(Uri.of("file:///data/config.json")) {
    println("Resource updated")
}
```

## Prompts

```kotlin
val prompts = mcp.prompts()

val list: List<McpPrompt> = prompts.list().getOrThrow()

val result: PromptResponse = prompts.get(
    PromptName.of("summarize"),
    PromptRequest(mapOf("text" to "Hello world", "style" to "brief"))
).getOrThrow()

// PromptResponse is a sealed interface
when (result) {
    is PromptResponse.Ok -> result.messages.forEach { println(it) }
    is PromptResponse.Error -> println("Error: ${result.message}")
}
```

## Sampling (LLM from server → client)

```kotlin
val sampling = mcp.sampling()
sampling.onRequest { req ->
    // Server is requesting LLM generation from the client
    val response = myChat(ChatRequest(req.messages, ModelParams(...)))
    sequence { yield(SamplingResponse(...)) }
}
```

## LLM Tool Integration

```kotlin
// Convert MCP tools to LLM tools for use with Chat
val llmTools: List<LLMTool> = McpLLMTools.fromClient(mcp).getOrThrow()

val response = chat(ChatRequest(
    messages,
    ModelParams(modelName, tools = llmTools)
))
```

## MCP Header Filters (HTTP Clients)

`HttpStreamingMcpClient` and `HttpNonStreamingMcpClient` automatically populate MCP protocol headers for tool calls, prompt gets, and resource reads. These filters are not typically constructed directly, but are available for custom HTTP clients:

```kotlin
// Adds Mcp-Method and Mcp-Name headers to a request
PopulateMcpHeaders(method = McpRpcMethod.of("tools/call"), name = "calculator")

// Adds Mcp-Method, Mcp-Name, and optional Mcp-Param-* headers derived
// from tool schema x-mcp-header annotations and the call arguments
PopulateToolHeaders(
    lastTools = listOfTools,
    method = McpRpcMethod.of("tools/call"),
    name = ToolName.of("calculator"),
    arguments = mapOf("operation" to "add")
)
```

`PopulateToolHeaders` reads `x-mcp-header` annotations from each tool's `inputSchema.properties` to determine which argument values should be promoted to `Mcp-Param-*` headers. Tools that don't use `x-mcp-header` annotations are unaffected.

## OAuth-Authenticated MCP Clients

`ClientFilters.DiscoveredMcpOAuth` discovers the authorization server from the MCP resource's `WWW-Authenticate` header (or `.well-known/oauth-protected-resource` fallback) and obtains tokens automatically. Three overloads:

```kotlin
// 1. Simple — client_credentials for both initial grant and refresh
ClientFilters.DiscoveredMcpOAuth(
    clientCredentials = Credentials("client-id", "secret"),
    scopes = listOf("mcp:read"),
    clock = Clock.systemUTC()
).then(httpClient)

// 2. Custom initial grant, client_credentials refresh
ClientFilters.DiscoveredMcpOAuth(
    clientCredentials = Credentials("client-id", "secret"),
    oAuthFlowFilter = ClientFilters.OAuthJwtAssertion(jwtAssertion)
).then(httpClient)

// 3. Fully pluggable grant and refresh
ClientFilters.DiscoveredMcpOAuth(
    oAuthFlowFilter = myGrantFilter,
    oAuthRefreshFilter = { refreshToken -> myRefreshFilter(refreshToken) }
).then(httpClient)
```

## Testing

```kotlin
val testClient = TestMcpClient()
// Configure with fake tools, resources, prompts for unit testing
```

## Gotchas

- **Constructor parameter order**: `baseUri`/`sseRequest`/`wsRequest` is the first parameter. Name and version are optional with defaults.
- Always use `client.use { }` or call `client.stop()` to release connections
- `start()` must be called before any operations
- Override timeouts per-call: `tools.list(overrideDefaultTimeout = 30.seconds)`
- `onChange` callbacks fire on the transport thread — don't block
- **`ResourceResponse`, `PromptResponse`, `CompletionResponse` are sealed interfaces**: Pattern-match on `Ok` and `Error` subtypes. Domain errors from the server are reconstructed as `*.Error` (not `McpError.Protocol`) when the server returns error code `-32050`.
- **Tool list must be fetched before calling**: `HttpNonStreamingMcpClient` and `HttpStreamingMcpClient` cache the last-known tools list after `tools.list()`. Calling `tools.call()` before `tools.list()` means `PopulateToolHeaders` has no schema to read, so `Mcp-Param-*` headers won't be set.
