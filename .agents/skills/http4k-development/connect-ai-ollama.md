---
license: Apache-2.0
module: http4k-connect-ai-ollama
---

# http4k-connect-ai-ollama Reference

Ollama client — connect actions for locally-running Ollama LLM server.

## Client

```kotlin
val ollama = Ollama.Http(
    baseUri = Uri.of("http://localhost:11434"),  // optional, this is the default
    http = JavaHttpClient()                       // optional
)
```

No API key required — Ollama runs locally.

## Chat Completion

```kotlin
val result = ollama.chatCompletion(
    model = ModelName.of("llama3.2"),
    messages = listOf(Message.User("Hello")),
    options = OllamaOptions(temperature = 0.7, num_predict = 1024)
)

result.successValue().forEach { chunk ->
    println(chunk.message?.content)
}
```

## Pull a Model

```kotlin
ollama.pullModel(
    model = ModelName.of("llama3.2"),
    stream = true
).successValue().forEach { status ->
    println("${status.status}: ${status.completed}/${status.total}")
}
```

## List Local Models

```kotlin
ollama.listLocalModels().successValue()
    .models.forEach { println(it.name) }
```

## Generate Embeddings

```kotlin
ollama.generateEmbeddings(
    model = ModelName.of("nomic-embed-text"),
    prompt = "Hello world"
).successValue().embedding
```

## Message Builders

```kotlin
Message.User("Hello")
Message.System("You are helpful")
Message.Assistant("Previous response")
```

## Structured Output (JSON Schema)

`ResponseFormat` is a sealed class with two variants:

```kotlin
// Force JSON output (no schema enforcement)
val result = ollama.chatCompletion(
    model = ModelName.of("llama3.2"),
    messages = listOf(Message.User("Extract data")),
    format = ResponseFormat.json
)

// Structured output with JSON schema enforcement
val schema = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "name" to mapOf("type" to "string"),
        "age" to mapOf("type" to "integer")
    ),
    "required" to listOf("name", "age")
)
val result = ollama.chatCompletion(
    model = ModelName.of("llama3.2"),
    messages = listOf(Message.User("Extract name and age from: John is 30")),
    format = ResponseFormat.Schema(schema)
)
```

## Gotchas

- No authentication — Ollama is a local service
- Model must be pulled locally before use (`pullModel`)
- `chatCompletion` returns `Sequence<CompletionResponse>` (streaming)
- Default port is `11434`
- `ResponseFormat` is a sealed class, not an enum — use `ResponseFormat.json` (data object) or `ResponseFormat.Schema(map)` (data class)
