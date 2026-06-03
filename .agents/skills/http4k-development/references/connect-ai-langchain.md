---
license: Apache-2.0
module: http4k-connect-ai-langchain
---

# http4k-connect-ai-langchain Reference

Bridge between http4k connect clients and LangChain4j — use http4k as the HTTP transport for LangChain4j models.

## HTTP Client Bridge

```kotlin
// Use any http4k HttpHandler as LangChain4j's HTTP client
val lcjHttpClient = Http4kLangchainHttpClient(JavaHttpClient())

// With http4k filters (auth, logging, retry, etc.)
val lcjHttpClient = Http4kLangchainHttpClient(
    DebuggingFilters.PrintRequestAndResponse()
        .then(RetryFilter(3))
        .then(JavaHttpClient())
)
```

## LangChain4j Models via http4k

```kotlin
// OpenAI via http4k
val model = OpenAIChatLanguageModel.builder()
    .httpClient(Http4kLangchainHttpClient(JavaHttpClient()))
    .apiKey("sk-...")
    .modelName("gpt-4o")
    .build()

// Ollama via http4k
val model = OllamaChatLanguageModel.builder()
    .httpClient(Http4kLangchainHttpClient(JavaHttpClient()))
    .baseUrl("http://localhost:11434")
    .modelName("llama3.2")
    .build()

// LM Studio via http4k
val model = LmStudioChatLanguageModel.builder()
    .httpClient(Http4kLangchainHttpClient(JavaHttpClient()))
    .build()
```

## Embedding Models

```kotlin
val embeddings = OpenAIEmbeddingModel.builder()
    .httpClient(Http4kLangchainHttpClient(JavaHttpClient()))
    .apiKey("sk-...")
    .modelName("text-embedding-3-small")
    .build()

val ollamaEmbeddings = OllamaEmbeddingModel.builder()
    .httpClient(Http4kLangchainHttpClient(JavaHttpClient()))
    .modelName("nomic-embed-text")
    .build()
```

## Document Loading from S3

```kotlin
val loader = S3DocumentLoader(s3Bucket)
val docs = loader.loadDocuments(listOf("README.md", "guide.pdf"))
```

## Gotchas

- This is a **bridge** module — it does not provide LangChain4j itself
- Add LangChain4j dependencies separately (`dev.langchain4j:langchain4j-*`)
- The main benefit is using http4k filters on LangChain4j calls (logging, retry, auth)
- SSE/streaming responses are handled via `ServerSentEventParser`
