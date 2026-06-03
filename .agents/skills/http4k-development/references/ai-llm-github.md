---
license: Apache-2.0
module: http4k-ai-llm-github
---

# http4k-ai-llm-github Reference

GitHub Models provider for http4k LLM interfaces.

## Chat

```kotlin
val chat = Chat.GitHubModels(
    token = ApiKey.of("ghp_..."),  // GitHub personal access token
    http = JavaHttpClient()         // optional
)

val result = chat(ChatRequest(messages, ModelParams(ModelName.of("gpt-4o"))))
```

## Streaming Chat

```kotlin
val streaming = StreamingChat.GitHubModels(
    token = ApiKey.of("ghp_..."),
    http = JavaHttpClient()
)
```

## Available Models

GitHub Models marketplace includes models from OpenAI, Meta, Mistral, and others:
- `gpt-4o`, `gpt-4o-mini`
- `Meta-Llama-3.1-405B-Instruct`
- `Mistral-large`
- See GitHub Models marketplace for full list

## Gotchas

- Uses GitHub personal access token (classic or fine-grained with Models permission)
- Routes through Azure AI inference endpoint under the hood
- Rate limits apply based on GitHub plan
- Free tier available for testing with lower rate limits
