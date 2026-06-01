---
license: Apache-2.0
module: http4k-connect-amazon-cloudfront
---

# http4k-connect-amazon-cloudfront Reference

CloudFront client — connect actions for Amazon CloudFront CDN.

## Client

```kotlin
val cf = CloudFront.Http(
    region = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient()   // optional
)
```

## Cache Invalidation

```kotlin
val invalidation = cf.createInvalidation(
    distributionId = DistributionId.of("EDFDVBD6EXAMPLE"),
    paths = listOf("/images/*", "/css/main.css"),
    callerReference = CallerReference.of("my-deploy-${System.currentTimeMillis()}")
).successValue()

val invalidationId = invalidation.Invalidation.Id
```

## Gotchas

- Uses XML/REST protocol (not JSON)
- `callerReference` must be unique per invalidation request — use deploy timestamp or UUID
- Wildcard paths (`/images/*`) invalidate all objects under that path
- Invalidations are eventually consistent — propagation takes time across edge locations
- Costs apply for invalidation paths beyond free tier (first 1000 paths/month free)
- Root invalidation `/*` invalidates everything but is expensive in path count terms
