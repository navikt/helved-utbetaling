---
module: http4k-ai-a2a-sdk
license: http4k Commercial
---

# http4k-ai-a2a-sdk Reference

Server SDK for implementing A2A agents in http4k. Provides `A2A` protocol facade, JSON-RPC and REST routing functions, task/push-notification storage, and JUnit Wiretap integration.

## Routing: Creating a Server

Two protocol bindings are available. Both return a `PolyHandler` (HTTP + SSE):

### JSON-RPC binding

`messageHandler` is the **last parameter** in all `a2aJsonRpc` overloads — it can be passed as a trailing lambda:

```kotlin
// Simplest form — single endpoint, all operations via POST
val server: PolyHandler = a2aJsonRpc(
    agentCard = AgentCard(
        name = "my-agent",
        version = Version.of("1.0.0"),
        description = "Processes requests"
    )
) { request ->
    Task(
        id = TaskId.random(),
        contextId = ContextId.random(),
        status = TaskStatus(state = TASK_STATE_COMPLETED),
        history = listOf(request.message)
    )
}

server.asServer(Jetty(9000)).start()
```

### REST binding

`messageHandler` is the **last parameter** in all `a2aRest` overloads — use as a trailing lambda:

```kotlin
val server: PolyHandler = a2aRest(
    agentCard = agentCard,
    basePath = "/v1"   // optional prefix for all routes
) { request ->
    myHandler(request)
}
```

### With explicit A2A facade

When you need access to the `A2A` instance for testing or shared storage:

```kotlin
val tasks = TaskStorage.InMemory()
val a2a = A2A(
    agentCard = agentCard,
    tasks = tasks,
    handler = myHandler
)

val server = a2aJsonRpc(a2a, rpcPath = "/")
```

### Direct asServer extension

```kotlin
// Extension on MessageHandler
myHandler.asServer(
    cfg = Jetty(9000),
    agentCard = agentCard,
    tasks = TaskStorage.InMemory(),
    rpcPath = "/"
)
```

## A2A Facade

`A2A` is the protocol coordinator. It decouples the transport (JSON-RPC/REST) from the agent logic and storage:

```kotlin
val a2a = A2A(
    agentCard = agentCard,
    tasks = TaskStorage.InMemory(),
    pushNotifications = PushNotificationConfigStorage.InMemory(),
    subscriptions = TaskSubscriptions.InMemory(),
    handler = myMessageHandler
)

// Direct protocol operations (used internally by routing functions)
val response = a2a.send(params, httpRequest)
val stream = a2a.stream(params, httpRequest)
val task = a2a.getTask(params)
```

### Multi-card support via AgentCardProvider

```kotlin
// Serve different agent cards per tenant
val cards = AgentCardProvider(defaultCard) // single card
// or implement AgentCardProvider for dynamic dispatch
```

## Storage

### TaskStorage

```kotlin
val tasks = TaskStorage.InMemory()   // default; thread-safe ConcurrentHashMap

// Store and retrieve
tasks.store(task)
val retrieved = tasks.get(TaskId.of("task-1"), historyLength = 10, tenant = null)

// List with filters
val page = tasks.list(
    contextId = ContextId.of("ctx-1"),
    status = TASK_STATE_COMPLETED,
    pageSize = 20,
    pageToken = null,
    historyLength = null,
    statusTimestampAfter = null,
    includeArtifacts = true
)

// Cancel
val cancelled = tasks.cancel(TaskId.of("task-1"))
```

`historyLength` trims the returned task's `history` to the last N entries — pass `null` for full history.

### Decorating TaskStorage

```kotlin
// NotifyingTaskStorage: calls a callback whenever a task is stored
val notifyingTasks = NotifyingTaskStorage(TaskStorage.InMemory()) { task ->
    subscriptions.notify(task)  // fan-out to SSE subscribers
}

// withSubscriptions: convenience wrapper combining storage + notification
val subscribingTasks = TaskStorage.InMemory().withSubscriptions(subscriptions)
```

### PushNotificationConfigStorage

```kotlin
val pushStore = PushNotificationConfigStorage.InMemory()

pushStore.store(taskPushConfig)
val config = pushStore.get(configId, tenant)
val page = pushStore.list(taskId, pageSize, pageToken, tenant)
pushStore.delete(configId, tenant)
```

### TaskSubscriptions

```kotlin
// InMemory: fan-out to SSE clients subscribed to a task
val subs = TaskSubscriptions.InMemory()
subs.subscribe(taskId, sse)  // called when SSE connection opens
subs.notify(task)            // sends update to all subscribers for task.id

// NoOp: disables SSE subscriptions (simpler for non-streaming agents)
val subs = TaskSubscriptions.NoOp()
```

### Push Notification Sender

```kotlin
// PushNotificationSender delivers HTTP callbacks when tasks change
val sender = PushNotificationSender(
    pushNotificationStore = pushStore,
    http = JavaHttpClient()
)
sender.send(task)  // sends a POST to all registered push URLs for the task
```

## Protocol Negotiation

`A2AProtocolNegotiation` middleware validates the `A2A-Version` header against the server's supported capabilities. It's applied automatically by `a2aJsonRpc` and `a2aRest`. If the client sends an unsupported version, the request is rejected with an appropriate error.

## JUnit Wiretap Integration

Requires `http4k-wiretap` (and transitively `http4k-ai-a2a-sdk`). Adds `Intercept.a2a` for wrapping A2A servers in tests:

```kotlin
@RegisterExtension
val intercept = Intercept.a2a(RenderMode.Always, baseUrl = Uri.of("http://localhost")) {
    // `this` is a Context
    A2A(AgentCard("name", Version.of("1.0.0"), "desc")) { request ->
        Message(
            messageId = MessageId.of("resp-1"),
            role = A2ARole.ROLE_AGENT,
            parts = listOf(Part.Text("ok"))
        )
    }
}

@Test
fun `test receives A2AClient`(client: A2AClient) {
    val card = client.agentCard().valueOrNull()!!
    assertThat(card.name, equalTo("name"))
}
```

`Intercept.a2a` automatically applies `PolyFilters.OpenTelemetryTracing` so all A2A traffic appears in the Wiretap trace view.

## Gotchas

- **`messageHandler` is the last parameter**: In `a2aJsonRpc` and `a2aRest`, the `messageHandler` parameter is always last so it can be passed as a trailing lambda. Named-parameter call sites need updating if they previously passed it positionally in a different position.
- **`PolyHandler` not `HttpHandler`**: Both `a2aJsonRpc` and `a2aRest` return `PolyHandler`. Call `PolyHandler.asServer(PolyServerConfig)` — not `HttpHandler.asServer(ServerConfig)`.
- **SSE requires `PolyHandler`**: The SSE subscription endpoint (`tasks().subscribe()`) only works when the server is a `PolyHandler`. If you extract just the `http` part, SSE will return 404.
- **`TaskSubscriptions.NoOp()` is the default**: If you don't pass subscriptions, `NoOp()` is used by the convenience factory methods — SSE task subscriptions silently do nothing. Pass `TaskSubscriptions.InMemory()` explicitly if you need SSE push for task updates.
- **`PushNotificationConfigStorage` vs `TaskSubscriptions`**: Push notifications (webhooks via HTTP POST) and SSE subscriptions are separate mechanisms. Use both if you want to support both patterns.
- **`NotifyingTaskStorage` must be wired to subscriptions manually**: If you use `NotifyingTaskStorage`, its callback is responsible for calling `subscriptions.notify(task)`. The `withSubscriptions` extension does this automatically.
- **`PolyFilters.OpenTelemetryTracing` for MCP/A2A**: When wrapping a `PolyHandler` with OpenTelemetry, use `PolyFilters.OpenTelemetryTracing` (not `ServerFilters.OpenTelemetryTracing` which only covers `HttpHandler`).
