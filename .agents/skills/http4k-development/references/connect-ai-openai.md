---
license: Apache-2.0
module: http4k-connect-ai-openai
---

# http4k-connect-ai-openai Reference

OpenAI API client — low-level connect actions for OpenAI REST API.

## Client

```kotlin
val openAI = OpenAI.Http(
    apiKey = ApiKey.of("sk-..."),
    http = JavaHttpClient(),        // optional
    org = OpenAIApi.Org.of("...")  // optional
)
```

## Chat Completion

```kotlin
val result = openAI.chatCompletion(
    model = ModelName.of("gpt-4o"),
    messages = listOf(
        Message.System("You are helpful"),
        Message.User("What is Kotlin?")
    ),
    max_tokens = MaxTokens.of(1024),
    temperature = Temperature.of(0.7)
)

// Returns Sequence<CompletionResponse> (streaming-compatible)
result.successValue().forEach { chunk ->
    println(chunk.choices[0].delta?.content)
}
```

## Streaming

```kotlin
openAI.chatCompletion(model, messages, stream = true)
    .successValue()
    .forEach { chunk -> print(chunk.choices[0].delta?.content) }
```

## Embeddings

```kotlin
openAI.createEmbeddings(
    model = ModelName.of("text-embedding-3-small"),
    input = listOf("Hello world")
).successValue()
```

## Image Generation

```kotlin
openAI.generateImage(
    prompt = "A sunset over mountains",
    model = ModelName.of("dall-e-3"),
    size = Size.`1024x1024`,
    response_format = url,
    n = 1
).successValue()
```

## Models

```kotlin
openAI.getModels().successValue()   // list available models
```

## Message Builders

```kotlin
Message.User("Hello")
Message.System("You are a helpful assistant")
Message.Assistant("The answer is 42")
Message.ToolCalls(listOf(toolCall))
```

## Tool Calling

```kotlin
openAI.chatCompletion(
    model, messages,
    tools = listOf(Tool(
        type = "function",
        function = FunctionSpec(
            name = ToolName.of("get_weather"),
            description = "Get weather",
            parameters = mapOf("type" to "object", ...)
        )
    )),
    tool_choice = ToolChoice.Auto()
)
```

## Structured Output (JSON Schema Response Format)

```kotlin
// Use JsonSchemaSpec to name the schema and mark it strict
openAI.chatCompletion(
    model, messages,
    response_format = ResponseFormat.JsonSchema(
        JsonSchemaSpec(
            name = "my_response",
            schema = mapOf("type" to "object", "properties" to mapOf(...)),
            strict = true
        )
    )
)
```

`ResponseFormat.JsonSchema` serialises to the nested OpenAI wire format (`json_schema.name`, `json_schema.schema`). `ResponseFormatAdapterFactory` registered in `OpenAIMoshi` handles this mapping automatically.

## Log Probabilities

```kotlin
// Request log probs for each output token
openAI.chatCompletion(
    model, messages,
    logprobs = true,
    top_logprobs = 3   // up to 5 top alternatives per token
)

// Access in response
val tokenLogProbs: List<TokenLogProb>? = response.choices[0].logprobs?.content
// TokenLogProb has: token, logprob, bytes, top_logprobs: List<TopLogProb>
```

## Reasoning Content

`ChoiceDetail` exposes `reasoning` and `reasoning_content` fields for models that return chain-of-thought reasoning in the response.

## Gotchas

- `chatCompletion` always returns `Sequence<CompletionResponse>` (even non-streaming returns a single-element sequence)
- Non-streaming mode: iterate `successValue()` — it completes after one element
- `Temperature.ONE` is the default (not 0.7)
- `ResponseFormat.JsonSchema` requires `JsonSchemaSpec` to carry the schema name; the `name` field on `JsonSchema` reflects this and is serialised correctly by `OpenAIMoshi`
