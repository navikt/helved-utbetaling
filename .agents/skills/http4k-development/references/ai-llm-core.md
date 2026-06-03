---
license: Apache-2.0
module: http4k-ai-llm-core
---

# http4k-ai-llm-core Reference

Core LLM interfaces and types for chat, streaming, image generation, tools, and memory.

## Chat

```kotlin
val chat: Chat = Chat.OpenAI(apiKey)   // or Anthropic, Gemini, etc.

val result: LLMResult<ChatResponse> = chat(
    ChatRequest(
        messages = listOf(Message.User(listOf(Content.Text("Hello")))),
        params = ModelParams(modelName = ModelName.of("gpt-4o"))
    )
)

result.fold(
    { error -> println("Error: $error") },
    { response -> println(response.message.contents) }
)
```

## Streaming Chat

```kotlin
val streaming: StreamingChat = StreamingChat.OpenAI(apiKey)

streaming(request).fold(
    { error -> println("Error: $error") },
    { responses -> responses.forEach { println(it.message.contents) } }
)
```

## Image Generation

```kotlin
val imageGen: ImageGeneration = ImageGeneration.OpenAI(apiKey)
val result = imageGen(ImageRequest(
    model = ModelName.of("dall-e-3"),
    prompt = UserPrompt.of("A sunset over mountains"),
    responseFormat = ImageResponseFormat.url,
    size = Size.of("1024x1024"),
    mimeType = MimeType.of("image/png"),
    quantity = 1
))
```

## Message Model

```kotlin
sealed class Message {
    data class System(val text: String) : Message()
    data class User(val contents: List<Content>) : Message()
    data class Assistant(val contents: List<Content>, val toolRequests: List<ToolRequest>) : Message()
    data class ToolResult(val id: RequestId, val tool: ToolName, val text: String) : Message()
}
```

## Content Types

```kotlin
Content.Text("Hello, world!")
Content.Image(resource, DetailLevel.auto)
Content.Audio(resource)
Content.Video(resource)
Content.PDF(resource)
```

## Model Parameters

```kotlin
ModelParams(
    modelName = ModelName.of("gpt-4o"),
    temperature = Temperature.of(0.7),
    maxOutputTokens = MaxTokens.of(1024),
    stopSequences = listOf("\n\n"),
    tools = listOf(myTool),
    toolSelection = ToolSelection.Auto,
    topP = 0.9,
    responseFormat = ChatResponseFormat.JsonObject
)
```

## Tools

```kotlin
val tool = LLMTool(
    name = ToolName.of("get_weather"),
    description = "Get current weather for a location",
    inputSchema = mapOf(
        "type" to "object",
        "properties" to mapOf("location" to mapOf("type" to "string")),
        "required" to listOf("location")
    )
)
```

## Error Handling

```kotlin
typealias LLMResult<T> = Result4k<T, LLMError>

sealed interface LLMError {
    data class Http(val response: Response) : LLMError
    data object Timeout : LLMError
    data object NotFound : LLMError
    data class Internal(val cause: Exception) : LLMError
}
```

## Memory

```kotlin
val memory: LLMMemory = LLMMemory.InMemory()

val id = memory.create(initialMessages).getOrThrow()
memory.add(id, listOf(Message.User(listOf(Content.Text("Hello")))))
val messages = memory.read(id).getOrThrow()
memory.delete(id)
```
