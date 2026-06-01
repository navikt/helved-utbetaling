---
license: Apache-2.0
module: http4k-connect-slack
---

# http4k-connect-slack Reference

Slack client — connect actions for the Slack Web API and webhooks.

## Client

```kotlin
val slack = Slack.Http(
    token = { SlackToken.of(System.getenv("SLACK_BOT_TOKEN")) },
    http = JavaHttpClient()   // optional
)
```

## Post Messages

```kotlin
slack.postMessage(
    channel = ChannelId.of("C1234567890"),
    text = "Hello from http4k!"
).successValue()

// With blocks
slack.postMessage(
    channel = ChannelId.of("C1234567890"),
    text = "Fallback text",
    blocks = listOf(
        SectionBlock(text = MarkdownText("*Important* notification"))
    )
).successValue()
```

## Webhook Actions

```kotlin
slack.postWebhookMessage(
    webhookUrl = Uri.of("https://hooks.slack.com/services/T00/B00/xxx"),
    text = "Webhook message"
).successValue()
```

## Channel Operations

```kotlin
slack.listConversations().successValue()
slack.getConversationInfo(ChannelId.of("C1234567890")).successValue()
```

## Gotchas

- Token is a lambda (`() -> SlackToken`) — allows runtime token refresh
- No fake provided — use `MockHttpHandler` or recording for tests
- Bot tokens start with `xoxb-`, user tokens with `xoxp-`
- Channel IDs (C...) are preferred over names — names can change
- Webhook URLs are pre-authorised for a specific channel — no token needed for webhooks
- Rate limits: Tier 1 (1/min), Tier 2 (20/min), Tier 3 (50/min) depending on method
