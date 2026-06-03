---
license: Apache-2.0
module: http4k-connect-ai-anthropic
---

# http4k-connect-ai-anthropic Reference

Anthropic (Claude) API client — low-level connect actions for the Anthropic Messages API.

## Client

```kotlin
val anthropic = AnthropicAI.Http(
    apiKey = ApiKey.of("sk-ant-..."),
    apiVersion = ApiVersion._2023_06_01,  // required
    http = JavaHttpClient()               // optional
)
```

## Message Completion

```kotlin
val result = anthropic.messageCompletion(
    model = AnthropicModels.Claude_Sonnet_4_5,
    messages = listOf(Message.User("What is Kotlin?")),
    maxTokens = MaxTokens.of(1024),
    systemPrompt = SystemPrompt.of("You are helpful"),  // optional
    temperature = Temperature.of(0.7),                   // optional
    tools = listOf(myTool),                              // optional
    toolChoice = ToolChoice.Auto()                        // optional
)

val response = result.successValue()
println(response.content.first().text)
println(response.usage.input_tokens)
```

## Streaming

```kotlin
anthropic.messageCompletion(model, messages, maxTokens, stream = true)
    .successValue()
    .forEach { chunk -> print(chunk.delta?.text) }
```

## Message Builders

```kotlin
Message.User("Hello")
Message.System("System prompt")
Message.Assistant("Previous response")
```

## Content Types

```kotlin
// Text + image in same message
Message.User(listOf(
    Content.Image(source = Base64ImageSource("image/jpeg", base64Data)),
    Content.Text("What is in this image?")
))
```

## Tool Choice

```kotlin
ToolChoice.Auto()                      // model decides
ToolChoice.Any()                       // must use some tool
ToolChoice.Tool("get_weather")         // must use specific tool
ToolChoice.None                        // no tool use
```

## Models

```kotlin
AnthropicModels.Claude_Opus_4_1
AnthropicModels.Claude_Sonnet_4_5
AnthropicModels.Claude_Haiku_4_5
```

## Gotchas

- Uses `x-api-key` header (not Bearer token)
- `anthropic-version` header required — pass via `ApiVersion._2023_06_01`
- System prompt is a top-level field, not a message
- `maxTokens` is **required** for all requests
