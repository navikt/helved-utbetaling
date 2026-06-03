---
license: Apache-2.0
module: http4k-ai-llm-anthropic
---

# http4k-ai-llm-anthropic Reference

Anthropic (Claude) provider for http4k LLM interfaces.

## Chat

```kotlin
val chat = Chat.AnthropicAI(
    apiKey = ApiKey.of("sk-ant-..."),
    http = JavaHttpClient(),              // optional
    apiVersion = ApiVersion._2023_06_01, // optional
    metadata = null,                      // optional
    systemPrompt = SystemPrompt.of("You are a helpful assistant") // optional
)

val result = chat(ChatRequest(messages, params))
```

## Streaming Chat

```kotlin
val streaming = StreamingChat.AnthropicAI(
    apiKey = ApiKey.of("sk-ant-..."),
    http = JavaHttpClient()
)
```

## Models

Common model names:
- `claude-opus-4-5`
- `claude-sonnet-4-5`
- `claude-haiku-4-5`
- `claude-3-5-sonnet-20241022`
- `claude-3-5-haiku-20241022`

## System Prompt

Unlike OpenAI, Anthropic separates the system prompt from the messages list. Pass it via `systemPrompt` parameter rather than as a `Message.System`:

```kotlin
Chat.AnthropicAI(apiKey, systemPrompt = SystemPrompt.of("Be concise"))
```

## Gotchas

- `maxOutputTokens` defaults to `MaxTokens.of(64000)` if not specified in `ModelParams`
- `metadata` can include `userId` for abuse tracking
- API version defaults to `_2023_06_01`
- Delegates to `http4k-connect-ai-anthropic` under the hood
