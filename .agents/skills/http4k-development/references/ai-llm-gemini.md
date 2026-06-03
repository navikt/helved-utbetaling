---
license: Apache-2.0
module: http4k-ai-llm-gemini
---

# http4k-ai-llm-gemini Reference

Google Gemini provider for http4k LLM interfaces.

## Chat

```kotlin
val chat = Chat.Gemini(
    apiKey = ApiKey.of("AI..."),
    http = JavaHttpClient()  // optional
)

val result = chat(ChatRequest(messages, ModelParams(ModelName.of("gemini-1.5-pro"))))
```

## Streaming Chat

```kotlin
val streaming = StreamingChat.Gemini(
    apiKey = ApiKey.of("AI..."),
    http = JavaHttpClient()
)
```

## Models

Common model names:
- `gemini-2.0-flash`
- `gemini-2.0-flash-lite`
- `gemini-1.5-pro`
- `gemini-1.5-flash`
- `gemini-1.5-flash-8b`

## Gotchas

- Get an API key from Google AI Studio (aistudio.google.com)
- Gemini uses a different message format internally — the adapter handles conversion
- `System` messages are converted to Gemini's `systemInstruction` field
- `topK` is supported via `ModelParams.topK`
