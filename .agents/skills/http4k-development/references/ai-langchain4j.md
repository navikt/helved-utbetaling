---
license: Apache-2.0
module: http4k-ai-langchain4j
---

# http4k-ai-langchain4j Reference

LangChain4j integration — use http4k as the HTTP client for LangChain4j models.

## HTTP Client Adapter

```kotlin
// Use http4k's HttpHandler as LangChain4j's HTTP client
val lcjClient = Http4kLangchainHttpClient(JavaHttpClient())

// Or via builder
val lcjClient = Http4kLangchainHttpClientBuilder()
    .http(JavaHttpClient())
    .build()
```

## Model Integrations

```kotlin
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

// OpenAI via http4k
val model = OpenAIChatLanguageModel.builder()
    .httpClient(Http4kLangchainHttpClient(JavaHttpClient()))
    .apiKey("sk-...")
    .modelName("gpt-4o")
    .build()
```

## Embedding Models

```kotlin
val model = OpenAIEmbeddingModel.builder()
    .httpClient(Http4kLangchainHttpClient(JavaHttpClient()))
    .apiKey("sk-...")
    .build()
```

## Document Loading

```kotlin
val loader = S3DocumentLoader(s3Client, bucketName)
val documents = loader.loadDocuments(listOf("doc.txt", "guide.pdf"))
```

## Gotchas

- This module provides the http4k HTTP client bridge to LangChain4j
- Add LangChain4j model dependencies separately (e.g., `dev.langchain4j:langchain4j-open-ai`)
- Allows using http4k filters (auth, logging, retry) on LangChain4j HTTP calls
