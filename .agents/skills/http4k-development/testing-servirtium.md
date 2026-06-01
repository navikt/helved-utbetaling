---
license: Apache-2.0
module: http4k-testing-servirtium
---

# http4k-testing-servirtium Reference

Service virtualization — record and replay HTTP interactions for deterministic integration tests.

## Recording Mode

```kotlin
val recording = ServirtiumServer.Recording(
    name = "github-api",
    target = Uri.of("https://api.github.com"),
    storageProvider = InteractionStorage.Disk(File("src/test/resources/recordings")),
    options = object : InteractionOptions {
        override fun modify(request: Request) = request.removeHeader("Authorization")
        override fun modify(response: Response) = response.removeHeader("X-RateLimit-Remaining")
    },
    proxyClient = ApacheClient()
)

recording.start()
// ... run tests against recording.port() ...
recording.stop()
```

## Replay Mode

```kotlin
val replay = ServirtiumServer.Replay(
    name = "github-api",
    storageProvider = InteractionStorage.Disk(File("src/test/resources/recordings")),
    options = InteractionOptions.Defaults
)

replay.start()
// ... run tests against replay.port() — no real API calls ...
replay.stop()
```

## Interaction Storage

```kotlin
// File-based (one file per test, human-readable format)
InteractionStorage.Disk(File("recordings"))

// In-memory (for unit tests)
InteractionStorage.InMemory()
```

## Interaction Options

```kotlin
object : InteractionOptions {
    // Modify request before recording/matching
    override fun modify(request: Request) = request
        .removeHeader("Authorization")
        .removeHeader("User-Agent")

    // Modify response before recording/matching
    override fun modify(response: Response) = response
        .removeHeader("Date")
        .body(response.bodyString().replace("secret", "REDACTED"))
}
```

## Gotchas

- **Recording creates files**: Recording mode proxies to the real API and stores interactions to disk. Run once, then switch to replay.
- **Replay is deterministic**: Replay mode serves stored responses in order. No network calls.
- **Redact secrets**: Use `InteractionOptions.modify()` to strip auth headers and sensitive data before storing.
- **Human-readable format**: Stored interactions are plain text, reviewable in version control.
