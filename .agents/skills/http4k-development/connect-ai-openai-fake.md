---
license: Apache-2.0
module: http4k-connect-ai-openai-fake
---

# http4k-connect-ai-openai-fake Reference

In-memory fake OpenAI server for testing.

## Setup

```kotlin
val fakeOpenAI = FakeOpenAI()
val client = OpenAI.Http(ApiKey.of("test-key"), fakeOpenAI)
```

## Custom Completion Generators

```kotlin
val fake = FakeOpenAI(
    completionGenerators = mapOf(
        ModelName.of("gpt-4o") to { req ->
            // Return custom completions for tests
            sequenceOf(CompletionResponse(choices = listOf(...)))
        }
    )
)
```

## Custom Models

```kotlin
val fake = FakeOpenAI(
    models = Storage.InMemory<Model>().also { storage ->
        storage["my-custom-model"] = Model(ModelName.of("my-custom-model"), ...)
    }
)
```

## Image Generation

```kotlin
// Fake generates placeholder images at predictable URLs
val response = client.generateImage(...).successValue()
val imageUri = response.data.first().url!!
// URI follows pattern: http://localhost:{port}/{size}.png
```

## Implementing Test Contracts

```kotlin
class FakeOpenAITest : OpenAIContract {
    private val fake = FakeOpenAI()
    override val openAi = OpenAI.Http(ApiKey.of("test"), fake)
}

class RealOpenAITest : OpenAIContract {
    override val openAi = OpenAI.Http(ApiKey.of(System.getenv("OPENAI_API_KEY")))
}
```

## Chaos Testing

```kotlin
fake.returnStatus(Status.SERVICE_UNAVAILABLE)   // simulate downtime
fake.behave()                                    // restore normal behavior
```

## Log Probability Support

The fake supports `logprobs` and `top_logprobs` transparently. When `logprobs = true` is set in the request, the fake generates synthetic (deterministic) log probs for each token in the response. Token values are stable across runs with the same input, making fake-based tests reproducible.

## Gotchas

- Fake accepts any API key (Bearer auth is validated as truthy but not checked)
- Default models match real OpenAI model names
- Image generation returns localhost URLs pointing to fake-served PNG files
- Extends `ChaoticHttpHandler` — use `WithRunningFake` for integration tests
- Synthetic log probs are computed from token hash codes — they are deterministic but not realistic
