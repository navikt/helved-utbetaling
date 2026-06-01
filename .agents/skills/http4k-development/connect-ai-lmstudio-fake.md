---
license: Apache-2.0
module: http4k-connect-ai-lmstudio-fake
---

# http4k-connect-ai-lmstudio-fake Reference

In-memory fake LM Studio server for testing.

## Setup

```kotlin
val fake = FakeLMStudio()
val client = LMStudio.Http(Uri.of("http://ignored"), fake)
// Or:
val client = fake.client()
```

## Test Contracts

```kotlin
class FakeLMStudioTest : LMStudioContract {
    private val fake = FakeLMStudio()
    override val lmStudio = fake.client()
}
```

## Chaos Testing

```kotlin
fake.returnStatus(Status.SERVICE_UNAVAILABLE)
fake.behave()
```

## Gotchas

- Extends `ChaoticHttpHandler`
- OpenAI-compatible fake — shares implementation with `FakeOpenAI`
