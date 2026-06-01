---
license: http4k Commercial
module: http4k-ai-mcp-testing
---

# http4k-ai-mcp-testing Reference

In-memory MCP client for testing MCP servers without network transport.

## Construction

```kotlin
// In-memory testing against a PolyHandler (no network)
val client = mcpServer.testMcpClient()

// With specific client capabilities and protocol version
val client = TestMcpClient(
    poly = mcpServer,
    connectRequest = Request(POST, "/mcp"),
    clientCapabilities = ClientCapabilities.All,  // default
    protocolVersion = LATEST_VERSION              // default
)

// Or via McpClientFactory
val factory = McpClientFactory.Test(mcpServer)
val client = factory()

// HTTP client for integration tests against a running server
val factory = McpClientFactory.Http(Uri.of("http://localhost:8080/mcp"))
val client = factory()
```

## Lifecycle

Use `useClient {}` for automatic start and close:

```kotlin
val mcp = mcp(metadata, NoMcpSecurity).testMcpClient()

mcp.useClient {
    // client is started and initialized
    val tools = tools().list()  // Success<List<McpTool>>
    val result = tools().call(toolName, ToolRequest(mapOf("arg" to "value")))
}
// client is automatically closed
```

## Capability Testing

```kotlin
mcp.useClient {
    // Tools
    tools().list()                                          // Success<List<McpTool>>
    tools().call(name, ToolRequest(mapOf("foo" to "bar")))  // Success<ToolResponse>

    // Prompts
    prompts().list()                                        // Success<List<McpPrompt>>
    prompts().get(promptName, PromptRequest(mapOf(...)))    // Success<PromptResponse>

    // Resources
    resources().list()                                      // Success<List<McpResource>>
    resources().read(ResourceRequest(uri))                  // Success<ResourceResponse>
    resources().subscribe(uri) { /* on update */ }
    resources().unsubscribe(uri)

    // Completions
    completions().complete(ref, CompletionRequest(arg))     // Success<CompletionResponse>

    // Sampling (server-initiated)
    sampling().onSampled { request ->
        sequenceOf(SamplingResponse.Ok(model, role, content, stopReason))
    }

    // Elicitations (server-initiated)
    elicitations().onElicitation { request ->
        ElicitationResponse.Ok(action, content)
    }

    // Tasks
    tasks().list()                                          // Success<List<Task>>
    tasks().get(taskId)                                     // Success<Task>
    tasks().cancel(taskId)
    tasks().result(taskId)

    // Progress
    progress().onProgress { progress -> /* handle */ }
}
```

## Notification Expectations

Force-receive server-sent change notifications in tests:

```kotlin
mcp.useClient {
    tools().onChange { /* callback triggered by expectNotification */ }
    serverTools.items = emptyList()          // modify server state
    tools().expectNotification()             // receive and process the notification

    resources().subscribe(uri) { /* called on update */ }
    serverResources.triggerUpdated(uri)
    resources().expectSubscriptionNotification(uri)

    // Also available for prompts and tasks
    prompts().expectNotification()
    tasks().expectNotification()
    elicitations().expectCompleteNotification(elicitationId)
}
```

## Gotchas

- **Use `mcp()` not `mcpHttpStreaming()`**: `mcpHttpStreaming()` is deprecated — use `mcp()` as a direct replacement.
- `testMcpClient()` only supports HTTP Streaming transport; for non-streaming, use `HttpNonStreamingMcpClient` directly
- All results are wrapped in `McpResult` (Result4k) — use `Success`/`Failure` pattern matching
- `expectNotification()` must be called explicitly to process pending server notifications in tests
- `useClient {}` calls `start()` and `close()` automatically — do not call them manually inside the block
- Sampling and elicitation handlers must be registered before invoking the tool that triggers them
- **Tool arguments accept any type**: `ToolRequest(mapOf("arg" to 42))` works — arguments are `Map<String, Any>`, not `Map<String, String>`
