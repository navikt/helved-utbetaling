---
license: Apache-2.0
module: http4k-connect-ai-azure
---

# http4k-connect-ai-azure Reference

Azure OpenAI Service client — connect actions for Azure-hosted OpenAI models.

## Client

```kotlin
val azure = AzureOpenAI.Http(
    apiKey = ApiKey.of("..."),
    apiBase = Uri.of("https://my-resource.openai.azure.com"),
    http = JavaHttpClient()   // optional
)
```

## Chat Completion

```kotlin
// deployment name replaces model name in Azure
val result = azure.chatCompletion(
    deploymentId = DeploymentId.of("my-gpt4o-deployment"),
    messages = listOf(Message.User("Hello")),
    max_tokens = MaxTokens.of(1024)
)

result.successValue().forEach { chunk ->
    println(chunk.choices[0].delta?.content)
}
```

## API Version

Azure requires an API version query parameter:

```kotlin
AzureOpenAI.Http(apiKey, apiBase, apiVersion = AzureOpenAIApiVersion.of("2024-02-01"))
```

## Gotchas

- **Deployment name, not model name** — Azure routes by deployment ID, not `ModelName`
- API base URL format: `https://{resource-name}.openai.azure.com`
- API version is required (e.g., `2024-02-01`)
- Same request/response format as OpenAI — `Sequence<CompletionResponse>` returned
