---
license: Apache-2.0
module: http4k-connect-ai-azure-fake
---

# http4k-connect-ai-azure-fake Reference

In-memory fake Azure OpenAI server for testing.

## Setup

```kotlin
val fake = FakeAzureOpenAI()
val client = AzureOpenAI.Http(ApiKey.of("test"), Uri.of("http://ignored"), fake)
// Or use convenience method:
val client = fake.client()
```

## Chaos Testing

```kotlin
fake.returnStatus(Status.SERVICE_UNAVAILABLE)
fake.behave()
```

## Test Contracts

```kotlin
class FakeAzureOpenAITest : AzureOpenAIContract {
    private val fake = FakeAzureOpenAI()
    override val azureOpenAi = fake.client()
}
```

## Gotchas

- Extends `ChaoticHttpHandler`
- Ignores the `apiBase` URI when used directly as `HttpHandler`
