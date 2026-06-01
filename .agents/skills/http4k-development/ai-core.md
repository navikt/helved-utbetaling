---
license: Apache-2.0
module: http4k-ai-core
---

# http4k-ai-core Reference

Core value types for http4k AI modules.

## Value Types

```kotlin
ApiKey.of("sk-...")          // API authentication key
ModelName.of("gpt-4o")       // model identifier
SystemPrompt.of("You are...") // system prompt text
UserPrompt.of("Hello")        // user prompt text
Temperature.of(0.7)           // 0.0–1.0 validated
MaxTokens.of(4096)            // token limit
RequestId.of("req-123")       // message/request ID
ResponseId.of("resp-456")     // response ID
ToolName.of("search")         // tool identifier
```

## Role

```kotlin
Role.System
Role.User
Role.Assistant
```

## Token Usage

```kotlin
data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int
)
```

## Stop Reason

```kotlin
StopReason.EndTurn
StopReason.MaxTokens
StopReason.StopSequence
StopReason.ToolUse
```

## Note

All value types use `dev.forkhandles.values` for compile-time type safety and validation. Import this module transitively via `http4k-ai-llm-core` or any LLM provider module.
