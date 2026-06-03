---
license: Apache-2.0
module: http4k-connect-ai-lmstudio
---

# http4k-connect-ai-lmstudio Reference

LM Studio client — connect actions for locally-running LM Studio server.

## Client

```kotlin
val lmStudio = LMStudio.Http(
    baseUri = Uri.of("http://localhost:1234"),  // optional, this is the default
    http = JavaHttpClient()                      // optional
)
```

No API key required — LM Studio runs locally.

## Chat Completion

```kotlin
val result = lmStudio.chatCompletion(
    model = ModelName.of("local-model"),
    messages = listOf(
        Message.System("You are helpful"),
        Message.User("Hello")
    ),
    max_tokens = MaxTokens.of(1024)
)

result.successValue().forEach { chunk ->
    println(chunk.choices[0].delta?.content)
}
```

## List Models

```kotlin
lmStudio.getModels().successValue()
```

## Gotchas

- OpenAI-compatible API — uses same request/response format as `connect-ai-openai`
- No authentication required
- Model must be loaded in LM Studio before use
- Default port is `1234` (LM Studio's default)
- `chatCompletion` returns `Sequence<CompletionResponse>` (streaming-compatible)
