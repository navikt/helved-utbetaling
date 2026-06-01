---
license: Apache-2.0
module: http4k-testing-tracerbullet
---

# http4k-testing-tracerbullet Reference

Request flow tracing and visualization. Record events during tests, then render as sequence diagrams.

## JUnit5 Extension

```kotlin
@RegisterExtension
val events = TracerBulletEvents(
    tracers = listOf(HttpTracer()),
    renderers = listOf(MermaidSequenceDiagram),
    traceRenderPersistence = TraceRenderPersistence.FileSystem(File("docs/traces")),
    tracePersistence = TracePersistence.InMemory(),
    reporter = TraceReporter.PrintToConsole,
    recordingMode = RecordingMode.Auto,
    renderingMode = RenderingMode.Always,
    reportingMode = ReportingMode.OnFailure
)

@Test
fun `user journey`() {
    // events implements Events — use as event sink
    val app = AddZipkinTraces().then(events).then(myApp)
    app(Request(GET, "/api/users"))
    // Traces rendered automatically after test
}
```

## TracerBullet (Standalone)

```kotlin
val tracerBullet = TracerBullet(HttpTracer(), CustomTracer())
val traces = tracerBullet(collectedEvents)
```

## Renderers

```kotlin
MermaidSequenceDiagram   // Mermaid.js sequence diagram
PumlSequenceDiagram      // PlantUML sequence diagram
D2SequenceDiagram        // D2 sequence diagram
MarkdownDocument         // Markdown table
```

## Persistence

```kotlin
// Rendered output storage
TraceRenderPersistence.FileSystem(File("docs/traces"))
TraceRenderPersistence.InMemory()

// Raw trace storage
TracePersistence.FileSystem(File("traces"))
TracePersistence.InMemory()
```

## Recording Modes

```kotlin
RecordingMode.Auto    // automatically record all events
RecordingMode.Manual  // manually control recording
```

## Gotchas

- **Requires ZipkinTraces**: Use `AddZipkinTraces()` filter to add trace context to events for correlation.
- **After-test rendering**: Traces are rendered after each test execution via `AfterTestExecutionCallback`.
- **Multiple renderers**: Pass multiple renderers to generate diagrams in different formats simultaneously.
