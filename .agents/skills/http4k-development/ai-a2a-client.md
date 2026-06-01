---
module: http4k-ai-a2a-client
license: http4k Commercial
---

# http4k-ai-a2a-client Reference

A2A client implementations for connecting to remote agents. Provides `HttpA2AClient` (JSON-RPC over HTTP) and `RestA2AClient` (REST API), both implementing `A2AClient`.

## A2AClient Interface

```kotlin
interface A2AClient : AutoCloseable {
    fun agentCard(): A2AResult<AgentCard>
    fun extendedAgentCard(): A2AResult<AgentCard>
    fun message(message: Message, configuration: SendMessageConfiguration? = null,
                metadata: Map<String, Any>? = null): A2AResult<MessageResponse>
    fun messageStream(message: Message, configuration: SendMessageConfiguration? = null,
                      metadata: Map<String, Any>? = null): A2AResult<MessageResponse>
    fun tasks(): Tasks
    fun pushNotificationConfigs(): PushNotificationConfigs
}
```

`messageStream()` returns an `A2AResult<ResponseStream>` where the response streams as SSE. `message()` waits for the complete response.

## HttpA2AClient (JSON-RPC)

Communicates using the JSON-RPC protocol binding — all requests go to a single endpoint:

```kotlin
val client = HttpA2AClient(
    baseUri = Uri.of("http://my-agent.example.com"),
    http = JavaHttpClient(responseBodyMode = Stream), // default; Stream required for SSE
    tenant = null,                                    // optional: Tenant for multi-tenant agents
    protocolVersion = ProtocolVersion.LATEST_VERSION  // sends A2A-Version header
)

// Get the agent's capabilities
val card = client.agentCard().valueOrNull()!!

// Send a message and get a response
val response = client.message(
    Message(
        messageId = MessageId.of(UUID.randomUUID().toString()),
        role = A2ARole.ROLE_USER,
        parts = listOf(Part.Text("Hello agent"))
    )
)
```

## RestA2AClient (REST)

Communicates using the HTTP/REST protocol binding — each operation maps to a distinct endpoint:

```kotlin
val client = RestA2AClient(
    baseUri = Uri.of("http://my-agent.example.com"),
    http = JavaHttpClient(responseBodyMode = Stream),
    tenant = null,
    protocolVersion = ProtocolVersion.LATEST_VERSION
)
```

The REST and JSON-RPC clients implement the same `A2AClient` interface and are interchangeable.

## Sending Messages

```kotlin
val message = Message(
    messageId = MessageId.of(UUID.randomUUID().toString()),
    role = A2ARole.ROLE_USER,
    parts = listOf(Part.Text("Process order #123"))
)

// Blocking send — waits for final Task or Message
when (val result = client.message(message)) {
    is Success -> when (val response = result.value) {
        is Task -> println("Task ${response.id}: ${response.status.state}")
        is Message -> println("Direct reply: ${response.parts}")
        is ResponseStream -> error("Unexpected stream for blocking call")
    }
    is Failure -> handleError(result.reason)
}

// Streaming — returns ResponseStream of intermediate + final items
when (val result = client.messageStream(message)) {
    is Success -> {
        val stream = result.value as ResponseStream
        for (item in stream) {
            when (item) {
                is Task -> println("Status: ${item.status.state}")
                is Message -> println("Message: ${item.parts}")
                is TaskStatusUpdateEvent -> println("Status update")
                is TaskArtifactUpdateEvent -> println("Artifact: ${item.artifact}")
            }
        }
    }
    is Failure -> handleError(result.reason)
}
```

## Task Operations

```kotlin
val tasks = client.tasks()

// Get a task by ID
val task = tasks.get(TaskId.of("task-123")).valueOrNull()

// List tasks with filtering
val page = tasks.list(
    contextId = ContextId.of("ctx-456"),
    status = TASK_STATE_COMPLETED,
    pageSize = 10,
    pageToken = null
).valueOrNull()

// Cancel a task
val cancelled = tasks.cancel(TaskId.of("task-123")).valueOrNull()

// Subscribe to task updates via SSE
val updates = tasks.subscribe(TaskId.of("task-123"))
```

## Push Notification Configs

```kotlin
val configs = client.pushNotificationConfigs()

// Register a push notification endpoint
val config = configs.set(
    taskId = TaskId.of("task-123"),
    url = Uri.of("https://my-service/webhook"),
    token = "bearer-token",
    authentication = null
).valueOrNull()

// List configs for a task
val list = configs.list(TaskId.of("task-123")).valueOrNull()

// Delete a config
configs.delete(TaskId.of("task-123"), config!!.id)
```

## Testing with In-Process Server

Use `PolyHandler.testA2AJsonRpcClient()` or `PolyHandler.testA2ARestClient()` to create a client directly from a server handler without network:

```kotlin
// From http4k-ai-a2a-client testFixtures
val serverHandler: PolyHandler = a2aJsonRpc(agentCard, myHandler)

// Routes through PolyHandler in-process — no real HTTP
val client = serverHandler.testA2AJsonRpcClient()
val card = client.agentCard().valueOrNull()

// REST variant
val restClient = serverHandler.testA2ARestClient()
```

This is ideal for contract tests — define an `A2AClientContract` abstract class and instantiate with different client + server pairs.

## Gotchas

- **`BodyMode.Stream` is required**: Pass `JavaHttpClient(responseBodyMode = Stream)` — the default `JavaHttpClient` buffers the body, breaking SSE streaming for `messageStream()` and `tasks().subscribe()`.
- **Close clients when done**: Both clients implement `AutoCloseable`. Use `client.use { ... }` or call `close()`.
- **`messageStream()` for agents with `streaming = false`**: If the agent card says `capabilities.streaming = false`, calling `messageStream()` may return an error or fall back to a single-item stream. Check `AgentCapabilities.streaming` first.
- **Tenant header**: If the agent is multi-tenant, pass `tenant` to the client constructor — it's included in every request. Don't set it per-request.
- **JSON-RPC IDs are auto-incremented**: `HttpA2AClient` manages request IDs internally; don't attempt to set them manually.
