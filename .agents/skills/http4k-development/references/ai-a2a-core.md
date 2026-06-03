---
module: http4k-ai-a2a-core
license: http4k Commercial
---

# http4k-ai-a2a-core Reference

Core types for the A2A (Agent-to-Agent) protocol: models, JSON serialization, and message handling interfaces. Required by both the client and server SDK modules.

## MessageHandler

`MessageHandler` is the primary extension point — the function an agent implements to process incoming messages:

```kotlin
typealias MessageHandler = (MessageRequest) -> MessageResponse

// MessageRequest contains:
// - message: Message
// - configuration: SendMessageConfiguration?
// - metadata: Map<String, Any>?
// - http: Request (the raw HTTP request)
```

`MessageResponse` is a sealed hierarchy: return a `Task`, a `Message`, or a `ResponseStream` (for streaming).

```kotlin
// Return a completed Task
val handler: MessageHandler = { request ->
    Task(
        id = TaskId.random(),        // generates a UUID-based TaskId
        contextId = ContextId.random(),  // generates a UUID-based ContextId
        status = TaskStatus(state = TASK_STATE_COMPLETED),
        history = listOf(request.message)
    )
}

// Return a direct Message (no task)
val directHandler: MessageHandler = {
    Message(
        messageId = MessageId.of(UUID.randomUUID().toString()),
        role = A2ARole.ROLE_AGENT,
        parts = listOf(Part.Text("I processed your request"))
    )
}

// Return a stream of intermediate updates followed by a final state
val streamHandler: MessageHandler = { request ->
    ResponseStream(sequence {
        yield(Task(id = TaskId.of("t1"), contextId = ContextId.of("c1"),
            status = TaskStatus(state = TASK_STATE_WORKING)))
        yield(Task(id = TaskId.of("t1"), contextId = ContextId.of("c1"),
            status = TaskStatus(state = TASK_STATE_COMPLETED)))
    })
}
```

`MessageFilter` composes handlers like http4k `Filter`:

```kotlin
val loggingFilter = MessageFilter { next ->
    { req ->
        println("Received: ${req.message.parts}")
        next(req)
    }
}
val composed = loggingFilter.then(handler)
```

## A2AResult

`A2AResult<T> = Result4k<T, A2AError>` — operations return this type:

```kotlin
sealed interface A2AError {
    data class Protocol(val error: ErrorMessage) : A2AError  // JSON-RPC error
    data class Http(val response: Response) : A2AError       // non-2xx HTTP response
    data object Timeout : A2AError
    data class Internal(val cause: Exception) : A2AError
}
```

Handle results with result4k:
```kotlin
when (val result = client.agentCard()) {
    is Success -> println(result.value.name)
    is Failure -> when (val err = result.reason) {
        is A2AError.Protocol -> println("RPC error: ${err.error.message}")
        is A2AError.Http -> println("HTTP ${err.response.status}")
        is A2AError.Timeout -> println("timed out")
        is A2AError.Internal -> throw err.cause
    }
}
```

## Core Models

### AgentCard

```kotlin
val card = AgentCard(
    name = "my-agent",
    version = Version.of("1.0.0"),
    description = "Processes customer requests",
    capabilities = AgentCapabilities(
        streaming = true,
        pushNotifications = true,
        extendedAgentCard = true
    ),
    skills = listOf(
        AgentSkill(
            id = SkillId.of("process-order"),
            name = "Process Order",
            description = "Handles order processing"
        )
    )
)
```

### Message

```kotlin
val msg = Message(
    messageId = MessageId.of(UUID.randomUUID().toString()),
    role = A2ARole.ROLE_USER,
    parts = listOf(Part.Text("Please process order #123"))
)
```

### Part (sealed)

```kotlin
Part.Text("plain text content")
Part.Raw(Base64Blob.encode(byteArray), mediaType = MimeType.of("image/png"))
Part.Url(Uri.of("https://example.com/doc.pdf"))
Part.Data(moshiNode)  // arbitrary JSON structure
```

Each `Part` subtype supports optional `metadata`, `filename`, and `mediaType` fields.

### Task

```kotlin
val task = Task(
    id = TaskId.of("task-123"),
    contextId = ContextId.of("ctx-456"),       // groups related tasks
    status = TaskStatus(
        state = TASK_STATE_COMPLETED,
        message = Message(...)                  // optional status message
    ),
    history = listOf(inputMessage, outputMessage),
    artifacts = listOf(Artifact(parts = listOf(Part.Text("result"))))
)
```

`TaskState` enum values: `TASK_STATE_SUBMITTED`, `TASK_STATE_WORKING`, `TASK_STATE_INPUT_REQUIRED`, `TASK_STATE_COMPLETED`, `TASK_STATE_CANCELED`, `TASK_STATE_FAILED`, `TASK_STATE_UNKNOWN`.

### ResponseStream

```kotlin
// ResponseStream wraps a Sequence<StreamItem>
// StreamItem = Task | Message | TaskArtifactUpdateEvent | TaskStatusUpdateEvent
val stream = ResponseStream(sequenceOf(workingTask, completedTask))

// Consume stream
val items = stream.toList()
val last = stream.last()
```

## A2AJson

`A2AJson` is a `ConfigurableMoshi` instance with all A2A value type adapters registered. Use it for serialization:

```kotlin
// Serialize
val json = A2AJson.asJsonString(task, Task::class)

// Deserialize
val task: Task = A2AJson.asA(jsonString)

// Parse to node then cast
val node = A2AJson.parse(jsonString) as MoshiObject
```

All A2A value types (`TaskId`, `MessageId`, `ContextId`, `Version`, etc.) are registered via `withA2AMappings()`. When creating custom JSON format for cross-module use, extend `ConfigurableA2AJson`:

```kotlin
object MyA2AJson : ConfigurableA2AJson(
    customJsonFactory = myAdditionalAdapterFactory,
    customMappings = { value(MyValueType) }
)
```

`typealias A2ANodeType = MoshiNode` — A2A uses Moshi nodes as its JSON node type.

## Gotchas

- **`ResponseStream` is consumed once**: It wraps a `Sequence<StreamItem>`, which can only be iterated once. Don't call `toList()` and then iterate again.
- **`A2ARole.ROLE_USER` vs `A2ARole.ROLE_AGENT`**: Messages from the user have `ROLE_USER`; agent replies use `ROLE_AGENT`. Mixing these up can cause clients to misinterpret conversation history.
- **`MessageId` must be globally unique**: Use `MessageId.random()` (or `UUID.randomUUID().toString()`) — duplicate IDs cause deduplication issues in clients.
- **`ContextId` groups tasks**: Multiple tasks with the same `contextId` belong to the same conversation/context. Use `ContextId.random()` for new contexts; reuse the same value across tasks in one conversation.
- **Value types require `A2AJson`**: Don't use a plain Moshi or Jackson instance for A2A types — value types like `TaskId`, `Version`, `ProtocolVersion` won't serialize/deserialize correctly without `withA2AMappings()`.
- **`TaskId.random()` and `ContextId.random()` use `SecureRandom` by default**: Pass a custom `Random` for deterministic test IDs.
