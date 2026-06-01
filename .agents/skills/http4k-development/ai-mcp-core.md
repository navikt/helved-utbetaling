---
license: http4k Commercial
module: http4k-ai-mcp-core
---

# http4k-ai-mcp-core Reference

Core Model Context Protocol (MCP) types — tools, resources, prompts, sampling.

## Tool Definition

```kotlin
val myTool = Tool(
    name = ToolName.of("calculator"),
    description = "Perform arithmetic",
    args = listOf(
        Tool.Arg.string() named "operation" described "add, sub, mul, div",
        Tool.Arg.int() named "a" described "first operand",
        Tool.Arg.int() named "b" described "second operand"
    )
)
```

## Tool Handler

```kotlin
val handler: ToolHandler = { req: ToolRequest ->
    val operation = Tool.Arg.string() named "operation" from req
    val a = Tool.Arg.int() named "a" from req
    val b = Tool.Arg.int() named "b" from req
    ToolResponse.Ok(listOf(Content.Text("Result: ${when(operation) {
        "add" -> a + b
        else -> "unknown"
    }}")))
}
```

## Arg Types

```kotlin
Tool.Arg.string()
Tool.Arg.nonEmptyString()
Tool.Arg.boolean()
Tool.Arg.int()
Tool.Arg.long()
Tool.Arg.double()
Tool.Arg.float()
Tool.Arg.uuid()
Tool.Arg.uri()
Tool.Arg.instant()
Tool.Arg.duration()
Tool.Arg.enum<MyEnum>()
Tool.Arg.value(MyValue)
Tool.Arg.status()            // maps to/from HTTP Status code
```

Non-string arg types (boolean, int, long, double, float) handle both native JSON types and string representations — `42` and `"42"` both work for `int()`.

## Resource Definition

```kotlin
// Static resource
Resource.Static(
    uri = Uri.of("file:///data/config.json"),
    name = ResourceName.of("config"),
    description = "Application config",
    mimeType = MimeType.of("application/json")
)

// Templated resource
Resource.Templated(
    uriTemplate = ResourceUriTemplate.of("file:///data/{filename}"),
    name = ResourceName.of("files"),
    description = "Data files"
)
```

## Resource Handler

```kotlin
val handler: ResourceHandler = { req: ResourceRequest ->
    ResourceResponse.Ok(listOf(
        Resource.Content.Text(
            uri = req.uri,
            text = File(req.uri.path).readText(),
            mimeType = MimeType.of("text/plain")
        )
    ))
}
```

Return `ResourceResponse.Error("message")` to surface a domain error to the client (transmitted via protocol error code `-32050` and reconstructed as `ResourceResponse.Error` on the client side).

## Prompt Definition

```kotlin
val myPrompt = Prompt(
    name = PromptName.of("summarize"),
    description = "Summarize text",
    args = listOf(
        Prompt.Arg.string() named "text" described "Text to summarize",
        Prompt.Arg.string() named "style" described "Brief or detailed"
    )
)
```

## Prompt Handler

```kotlin
val handler: PromptHandler = { req: PromptRequest ->
    val text = req["text"] ?: ""
    PromptResponse.Ok(
        messages = listOf(Message.User(listOf(Content.Text("Summarize: $text")))),
        description = "Summarization prompt"
    )
}
```

Return `PromptResponse.Error("message")` to surface a domain error to the client.

## Client Interface (Server-side, for callbacks)

```kotlin
// Available in ToolRequest.client, ResourceRequest.client, PromptRequest.client
fun handler(req: ToolRequest): ToolResponse {
    req.client.log("Processing request", LogLevel.info)
    req.client.progress(50, 100.0, "halfway done")
    req.client.requestRoots()               // ask connected client to send its roots list
    return ToolResponse.Ok(...)
}
```

`Client.updateTask(task, meta)` updates a long-running task state. The `meta` parameter defaults to `Meta.default`.

`Client.requestRoots(meta)` sends a `roots/list` request to the connected MCP client, triggering the client's registered roots callback.

## Meta and MetaKey Lens System

`Meta` wraps MCP `_meta` fields as a typed, lens-accessible object:

```kotlin
// Meta is a wrapper around MoshiObject
val meta = Meta()  // empty
val meta = Meta(lens1 of value1, lens2 of value2)  // with fields

// Access raw values by string key
val token: MoshiNode? = meta["progressToken"]
```

### MetaKey (Lens Spec)

`MetaKey` provides strongly-typed access to meta fields via lenses:

```kotlin
// Built-in meta keys
val progressLens = MetaKey.progressToken().toLens()
val traceParentLens = MetaKey.traceParent().toLens()
val traceStateLens = MetaKey.traceState().toLens()
val baggageLens = MetaKey.baggage().toLens()

// Read from meta
val token: ProgressToken? = progressLens(meta)

// Write to meta (returns new Meta)
val newMeta = Meta(progressLens of ProgressToken.of("abc"))

// Combine multiple fields
val meta = Meta(progressLens of token, traceParentLens of parent)
```

### Custom Meta Keys (Auto-Marshalled)

Use `MetaKey.auto()` with a `ConfigurableMcpJson` subclass for custom types:

```kotlin
// Define a field name and type
data class MyPayload(val data: String)

val myLens = MetaKey.auto(MetaField<MyPayload>("my/custom-key"), MyMoshi).toLens()

val meta = Meta(myLens of MyPayload("hello"))
val payload: MyPayload? = myLens(meta)  // nullable — returns null if key absent
```

### MetaField

`MetaField<T>` names a typed meta key for use with lens specs:

```kotlin
open class MetaField<T : Any>(val key: String)
```

## Elicitations

`ToolResponse.ElicitationRequired` asks the client for more information before the tool can complete:

```kotlin
ToolResponse.ElicitationRequired(
    elicitations = listOf(McpElicitations.Request.Params.Url(...)),
    message = "This request requires more information."
)
```

The `elicitations` list type is `List<McpElicitations.Request.Params.Url>`.

## Header Names and Lenses

The MCP protocol version is transmitted as `Mcp-Protocol-Version` (mixed-case). The lens accessor `Header.MCP_PROTOCOL_VERSION` uses this casing — do not use `MCP-Protocol-Version` when constructing requests manually.

```kotlin
Header.MCP_SESSION_ID       // Mcp-Session-Id — identifies the MCP session
Header.MCP_PROTOCOL_VERSION // Mcp-Protocol-Version — negotiated protocol version
Header.MCP_METHOD           // Mcp-Method — declares the MCP RPC method for the request
Header.MCP_NAME             // Mcp-Name — carries the tool/prompt/resource name
```

`Header.MCP_METHOD` and `Header.MCP_NAME` are required by the MCP DRAFT protocol version. Servers validate that these header values match the corresponding values in the JSON-RPC body — a mismatch returns a `HeaderMismatchError`. The HTTP MCP clients (`HttpStreamingMcpClient`, `HttpNonStreamingMcpClient`) set these headers automatically for tool calls, prompt gets, and resource reads.

`ProtocolVersion.DRAFT` is the constant for the draft protocol version that enables stricter header validation. Clients and servers negotiate the protocol version during the initialize handshake.

## ServerCapabilities Extensions

`ServerCapabilities` and `ClientCapabilities` support a generic `extensions` map for custom capability negotiation (e.g., payment protocols):

```kotlin
// Add extension data to ServerMetaData
val metadata = ServerMetaData("MyServer", "1.0.0")
    .withExtensions(myExtension)  // McpExtension instance

// Or add raw pairs
val capabilities = ServerCapabilities(...).withExtensions("payment" to mapOf("methods" to listOf("lightning")))
```

`McpExtension` implementors provide `name` and a config map. They appear in `ServerCapabilities.extensions[name]` during capability negotiation.

`ClientCapabilities` also carries an `extensions` field for client-declared capabilities.

## ToolFilter (Chaining)

```kotlin
// ToolFilter wraps tool handlers for cross-cutting concerns
val filter = ToolFilter { next -> { request -> next(request) } }

// Chain filter before a tool
val filteredTool = filter.then(myTool)
```

## Response Types (Sealed Interfaces)

`CompletionResponse`, `PromptResponse`, and `ResourceResponse` are sealed interfaces with `Ok` and `Error` subtypes. Always construct responses using the subtype, not the interface directly:

```kotlin
// Correct
CompletionResponse.Ok(listOf("value1", "value2"))
PromptResponse.Ok(listOf(Message(Role.Assistant, Text("response"))))
ResourceResponse.Ok(listOf(Resource.Content.Text(uri, "content")))

// Signal a domain error (transported via error code -32050)
CompletionResponse.Error("argument is required")
PromptResponse.Error("template not found")
ResourceResponse.Error("resource unavailable")
```

`SamplingResponse` and `ElicitationResponse` also have an `Error` subtype:

```kotlin
SamplingResponse.Error("LLM unavailable")
ElicitationResponse.Error("user cancelled")
```

On the **client side**, errors transmitted via domain error code `-32050` are reconstructed as `*.Error` subtypes rather than surfacing as `McpError.Protocol`.

## Initialize Types

`InitializeHandler` is the function type for handling MCP client-server handshake:

```kotlin
typealias InitializeHandler = (InitializeRequest) -> InitializeResponse

// InitializeRequest carries:
data class InitializeRequest(
    val clientInfo: VersionedMcpEntity,
    val capabilities: ClientCapabilities,
    val protocolVersion: ProtocolVersion,
    val connectRequest: Request? = null  // raw HTTP request
)

// InitializeResponse is a sealed interface:
sealed interface InitializeResponse {
    data class Ok(
        val serverInfo: VersionedMcpEntity,
        val capabilities: ServerCapabilities,
        val protocolVersion: ProtocolVersion,
        val instructions: String? = null
    ) : InitializeResponse

    data class Error(val message: String) : InitializeResponse
}
```

`InitializeFilter` wraps `InitializeHandler` for cross-cutting concerns (analogous to `HttpFilter`):

```kotlin
val filter = InitializeFilter { next -> { req -> next(req) } }
```

## TaskId

`TaskId` is used in long-running tool operations and sampling. Generate random IDs with:

```kotlin
val id = TaskId.random()  // UUID-based, uses SecureRandom by default
val id = TaskId.random(fixedRandom)  // deterministic for tests
val id = TaskId.of("my-task-id")    // from an existing string
```

## McpResult Helpers

```kotlin
typealias McpResult<T> = Result4k<T, McpError>

sealed interface McpError {
    data class Protocol(val error: ErrorMessage) : McpError
    data class Http(val response: Response) : McpError
    data object Timeout : McpError
    data class Internal(val cause: Exception) : McpError
}

// Coerce a McpResult to a specific type or throw AssertionError (useful in tests)
val response: PromptResponse.Ok = mcpResult.coerce<PromptResponse.Ok>()
```
