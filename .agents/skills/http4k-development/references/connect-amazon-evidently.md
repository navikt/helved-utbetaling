---
license: Apache-2.0
module: http4k-connect-amazon-evidently
---

# http4k-connect-amazon-evidently Reference

Evidently client — connect actions for Amazon CloudWatch Evidently (A/B testing and feature flags).

## Client

```kotlin
val evidently = Evidently.Http(
    region = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient()   // optional
)
```

## Gotchas

- Placeholder module — action coverage may be limited in the current version
- Uses JSON REST protocol (not JSON with X-Amz-Target)
- Evidently provides feature flag evaluation and experiment management
- Check the http4k-connect source for current action availability
