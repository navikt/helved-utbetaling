---
license: Apache-2.0
module: http4k-connect-ai-ollama-fake
---

# http4k-connect-ai-ollama-fake Reference

In-memory fake Ollama server for testing.

## Setup

```kotlin
val fake = FakeOllama()
val client = Ollama.Http(Uri.of("http://ignored"), fake)
// Or:
val client = fake.client()
```

## Test Contracts

```kotlin
class FakeOllamaTest : OllamaContract {
    private val fake = FakeOllama()
    override val ollama = fake.client()
}
```

## Chaos Testing

```kotlin
fake.returnStatus(Status.SERVICE_UNAVAILABLE)
fake.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Pre-populated with default Ollama model names
- `PullModel` is a no-op in the fake (models are always "available")
