---
license: http4k Commercial
module: http4k-tools-hotreload
---

# http4k-tools-hotreload Reference

Hot-reloading development server — recompiles and reloads the app on file changes.

## Setup

Your app class must implement `HotReloadable<HttpHandler>` and be a top-level class with a no-arg constructor:

```kotlin
class MyApp : HotReloadable<HttpHandler> {
    override fun create(): HttpHandler = routes(
        "/" bind GET to { Response(OK).body("Hello") }
    )
}
```

## Starting the Server

```kotlin
// HTTP handler
fun main() {
    HotReloadServer.http<MyApp>().start()
}

// Multi-protocol (HTTP + WebSocket + SSE)
fun main() {
    HotReloadServer.poly<MyPolyApp>().start()
}
```

## Configuration Options

```kotlin
HotReloadServer.http<MyApp>(
    serverConfig = SunHttp(8080),          // server to use (default: SunHttp)
    watcher = ProjectCompilingPathWatcher( // watches + compiles on change
        ProjectCompiler.Gradle()
    ),
    taskRunner = TaskRunner.retry(maxAttempts = 3, delay = 1.seconds),
    log = PrintStreams.STDOUT,
    error = PrintStreams.STDERR
)
```

## How It Works

`HotReloadRoutes` wraps your app with:
- Script injection into HTML responses (EventSource listener)
- `/http4k/ping` → `"pong"`
- `/http4k/hot-reload` → long-poll endpoint (default 10 min timeout), returns when change detected
- No-cache headers on all responses

On file change: recompiles via Gradle, instantiates `MyApp` via reflection, replaces handler.

## Gotchas

- App class must be **top-level** (non-inner, no constructor parameters)
- Instantiation is via reflection: `MyApp().create()`
- SunHttp recommended over Jetty for reload speed
- Only injects hot-reload script into HTML responses
- `ProjectCompilingPathWatcher` watches `build/classes/` and `src/` directories
