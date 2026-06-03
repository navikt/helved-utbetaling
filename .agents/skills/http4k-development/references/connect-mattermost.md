---
license: Apache-2.0
module: http4k-connect-mattermost
---

# http4k-connect-mattermost Reference

Mattermost client — connect actions for the Mattermost REST API.

## Client

```kotlin
val mattermost = Mattermost.Http(
    baseUri = Uri.of("https://mattermost.mycompany.com"),
    http = JavaHttpClient()   // optional
)
```

## Post Messages

```kotlin
mattermost.createPost(
    channelId = ChannelId.of("channel-id"),
    message = "Hello from http4k!"
).successValue()
```

## Channel Operations

```kotlin
mattermost.getChannel(channelId = ChannelId.of("channel-id")).successValue()
```

## Gotchas

- Authentication is typically embedded in the HTTP client (e.g., bearer token filter) rather than a constructor parameter
- No fake provided — use `MockHttpHandler` or recording for tests
- Self-hosted deployment: configure `baseUri` to your Mattermost server URL
- Mattermost uses channel IDs (not names) for most API calls
- API tokens are created per-user in Mattermost account settings
