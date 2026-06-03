---
license: http4k Commercial
module: http4k-wiretap
---

# http4k-wiretap Reference

Wiretap is an http4k Pro module — a development console that wraps an HTTP application, recording all traffic and OpenTelemetry spans so you can monitor, inspect, and test what happens inside your app in real time. Requires an http4k Pro license.

## Wrapping a Remote App (Proxy Mode)

```kotlin
// Point Wiretap at an already-running app
val wiretap = Wiretap(
    RemoteTarget(Uri.of("http://localhost:8080"))
)

// Start the Wiretap console (default port: 21000)
wiretap.asServer(Jetty(21000)).start()
// Open: http://localhost:21000/_wiretap
```

## Wrapping a Local App

Use the `LocalTarget` companion factory methods:

```kotlin
// Wrap an HttpHandler
val wiretap = Wiretap(LocalTarget.http { myApp() })

// Wrap a PolyHandler (HTTP + SSE/WS)
val wiretap = Wiretap(LocalTarget.poly { poly(myApp()) })

wiretap.asServer(Jetty(21000)).start()
```

`LocalTarget` vs `RemoteTarget`:
- `LocalTarget.http { ... }` — wraps an in-process `HttpHandler`; no network hop
- `LocalTarget.poly { ... }` — wraps a `PolyHandler`; auto-converts via `.toHttpHandler()`
- `RemoteTarget` — proxies to a real server URI; auto-starts the server

## Context in LocalTarget / RemoteTarget

Both target types receive a `Context` that provides test-friendly collaborators:

```kotlin
val wiretap = Wiretap(
    LocalTarget {
        val outboundClient = http()          // HttpHandler with outbound recording filter applied
        val clock = clock()                  // deterministic clock
        val otel = otel("my-service")        // OpenTelemetry instance wired to Wiretap stores
        MyApp(outboundClient, clock, otel)
    }
)
```

Use `ctx.http()` (not `JavaHttpClient()` directly) so outbound calls are recorded.
Use `ctx.otel(serviceName)` so spans appear in the Wiretap trace view.

## Full Configuration

```kotlin
val wiretap = Wiretap(
    target = RemoteTarget(Uri.of("http://localhost:8080")),
    transactionStore = TransactionStore.InMemory(),  // default
    traceStore = TraceStore.InMemory(),              // default
    security = BearerAuthSecurity { it == "secret" },// protect the console UI
    mcpOptions = WiretapMcpServerOptions(
        security = BearerAuthMcpSecurity { it == "secret" },
        path = "/mcp"                                // MCP endpoint path
    ),
    sanitise = { tx ->
        // return null to drop a transaction, or modify before recording
        if (tx.request.uri.path.startsWith("/health")) null else tx
    },
    bodyHydration = BodyHydration.All,  // record full bodies; use None for large payloads
    resetGlobalOtel = true,            // reset and configure GlobalOpenTelemetry on start (default true)
    livingDocSections = defaultLivingDocSections,    // customise living doc markdown sections
    traceReportTabs = defaultTraceReportTabs,        // customise trace report HTML tabs
)
```

## JUnit Integration: `Intercept`

`Intercept` is a JUnit 5 extension that wires up Wiretap for unit/integration tests. It records traffic and telemetry, and generates an HTML report on test failure.

Use the companion object factory methods to construct `Intercept` — these are the public API:

| Factory | Use case |
|---------|----------|
| `Intercept.http { HttpHandler }` | Wrap a plain HTTP app |
| `Intercept.poly { PolyHandler }` | Wrap a full PolyHandler (HTTP + SSE/WS) |
| `Intercept.mcp { PolyHandler }` | Wrap an MCP server |
| `Intercept.mcpCapabilities { List<ServerCapability> }` | Wrap individual MCP capabilities |
| `Intercept.a2a { A2A }` | Wrap an A2A agent server (requires `http4k-ai-a2a-sdk`) |
| `Intercept()` / `Intercept(renderMode)` | No-arg: default HTTP passthrough |

### HttpHandler app

```kotlin
class MyTest {
    @RegisterExtension
    @JvmField
    val intercept = Intercept.http {
        // `this` is a Context — provides http(), otel(), clock(), random()
        MyApp(http { Response(OK) }, otel("my-service"))
    }

    @Test
    fun `spans are recorded`(http: HttpHandler) {
        http(Request(GET, "/"))
    }
}
```

### Poly/MCP apps

```kotlin
// Wrap a full MCP server (PolyHandler)
@RegisterExtension
@JvmField
val intercept = Intercept.mcp {
    myMcpApp(http(), otel("my-service"))
}

// Wrap individual MCP ServerCapability (not a full server)
@RegisterExtension
@JvmField
val intercept = Intercept.mcpCapabilities {
    listOf(myToolCapability, myResourceCapability)
}

@Test
fun `test gets McpClient`(client: McpClient) {
    val tools = client.tools().list()
}
```

`Intercept.mcpCapabilities` automatically applies detail-level OTel span attributes for all MCP operation types (tool calls, completions, prompts, and resource reads).

### A2A agents (requires `http4k-ai-a2a-sdk`)

```kotlin
@RegisterExtension
val intercept = Intercept.a2a(baseUrl = Uri.of("http://localhost")) {
    A2A(AgentCard("my-agent", Version.of("1.0.0"), "desc")) { req ->
        Task(id = TaskId.of("t1"), contextId = ContextId.of("c1"),
            status = TaskStatus(TASK_STATE_COMPLETED))
    }
}

@Test
fun `test gets A2AClient`(client: A2AClient) {
    val card = client.agentCard().valueOrNull()!!
}
```

### RenderMode

```kotlin
Intercept.http(RenderMode.Always) { http() }   // Always generate report
Intercept.http(RenderMode.OnFailure) { http() } // OnFailure (default)
Intercept.http(RenderMode.Never) { http() }    // Never generate report
Intercept()                                     // OnFailure, default HTTP handler
Intercept(RenderMode.Always)                    // OnFailure, specified render mode
```

Reports are written to `build/reports/http4k/wiretap/<package>/<TestName>.<method>`. Each test generates two files:
- `<name>.html` — full interactive HTML report (traces, traffic, stdout/stderr)
- `<name>.md` — living document markdown with sequence diagram and HTTP transactions

The HTML report path is also published as a JUnit report entry and printed to stdout.

### Accessing recorded data in tests

Pass `traceStore`, `logStore`, `transactionStore` as parameters to share stores with the test:

```kotlin
class MyTest {
    private val traceStore = TraceStore.InMemory()
    private val transactionStore = TransactionStore.InMemory()

    @RegisterExtension
    @JvmField
    val intercept = Intercept.http(
        traceStore = traceStore,
        transactionStore = transactionStore
    ) { MyApp(http(), otel("my-service")) }

    @Test
    fun `spans are recorded`(http: HttpHandler) {
        http(Request(GET, "/"))
        assertThat(traceStore.traces(Ascending).size, greaterThan(0))
        assertThat(transactionStore.list(Descending).size, greaterThan(0))
    }
}
```

`WiretapTransaction.direction` is `Direction.Inbound` (server received) or `Direction.Outbound` (client sent).

## MCP Inspector UI

When the target app exposes an MCP server, the Wiretap console includes an MCP inspector with tabs for tools, resources, prompts, and completions. The completions tab lets you interactively test completion handlers against your MCP server.

## Gotchas

- **`Intercept { HttpHandler }` no longer compiles**: The primary constructor's `appFn` now takes `Context.() -> PolyHandler`. Use `Intercept.http { myHttpApp }` instead. `Intercept()` and `Intercept(renderMode)` still work for the no-appFn constructors.
- **Pro license required**: `http4k-pro-wiretap` requires an http4k Pro commercial license.
- **Use `ctx.http()` for outbound clients**: Passing `JavaHttpClient()` directly bypasses outbound traffic recording. Always use `http()` from `Context`.
- **Use `ctx.otel(name)` not `GlobalOpenTelemetry`**: `Intercept` resets `GlobalOpenTelemetry` before each test and injects a Wiretap-backed instance. Using `ctx.otel()` ensures spans are captured in `traceStore`.
- **`bodyHydration = BodyHydration.All` buffers bodies**: For streaming or large payloads, use `BodyHydration.None` to avoid memory pressure.
- **MCP auto-detection**: `RemoteTarget` checks `GET {mcpPath}` to detect whether the target supports MCP. Wiretap exposes MCP tools at `/_wiretap/mcp` regardless.
- **`sanitise` can drop transactions**: Return `null` from `sanitise` to exclude a transaction from recording (e.g., health checks, static assets).
- **Wiretap console at `/_wiretap`**: The UI is mounted under `/_wiretap`; all other paths proxy to the target.
