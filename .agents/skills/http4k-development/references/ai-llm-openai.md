---
license: Apache-2.0
module: http4k-ai-llm-openai
---

# http4k-ai-llm-openai Reference

OpenAI provider for http4k LLM interfaces.

## Chat

```kotlin
val chat = Chat.OpenAI(
    apiKey = ApiKey.of("sk-..."),
    http = JavaHttpClient(),       // optional
    org = OpenAIApi.Org.of("...")  // optional
)

val result = chat(ChatRequest(messages, params))
```

## Streaming Chat

```kotlin
val streaming = StreamingChat.OpenAI(
    apiKey = ApiKey.of("sk-..."),
    http = JavaHttpClient()
)
```

## Image Generation

```kotlin
val imageGen = ImageGeneration.OpenAI(
    apiKey = ApiKey.of("sk-..."),
    http = JavaHttpClient()
)
```

## OpenAI-Compatible Endpoints

For any OpenAI-compatible API (Ollama, LM Studio, local models):

```kotlin
val chat = Chat.OpenAI(openAICompatibleClient)
```

## Models

Common model names:
- `gpt-4o`, `gpt-4o-mini`
- `gpt-4-turbo`, `gpt-4`
- `gpt-3.5-turbo`
- `o1`, `o1-mini`
- `dall-e-3`, `dall-e-2` (image generation)

## Gotchas

- Requires `OPENAI_API_KEY` environment variable or explicit `ApiKey`
- Organization ID is optional but required for some API tiers
- Streaming uses chunked SSE responses
