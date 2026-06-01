---
license: Apache-2.0
module: http4k-connect-amazon-core
---

# http4k-connect-amazon-core Reference

Core AWS types, credentials, and SigV4 signing for all Amazon connect modules.

## Credentials Provider

```kotlin
// From environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN)
val creds = CredentialsProvider.Environment()

// From explicit map
val creds = CredentialsProvider.Environment(mapOf(
    "AWS_ACCESS_KEY_ID" to "AKIA...",
    "AWS_SECRET_ACCESS_KEY" to "secret..."
))

// Inline
val creds: CredentialsProvider = { AwsCredentials("key", "secret") }
```

## AWS Auth Filter

```kotlin
// Applied automatically by all Amazon Http factories — rarely needed directly
val http = ClientFilters.AwsAuth(
    scope = AwsCredentialScope("us-east-1", "s3"),
    credentialsProvider = CredentialsProvider.Environment(),
    clock = Clock.systemUTC(),
    payloadMode = Payload.Mode.Signed  // or Unsigned for S3 streaming
).then(JavaHttpClient())
```

## Region

```kotlin
Region.of("us-east-1")
Region.of("eu-west-2")
```

## Core Value Types

```kotlin
AwsAccount.of("123456789012")   // 12-digit AWS account ID
ARN.of("arn:aws:s3:::my-bucket")
```

## Action Base Classes

| Class | Protocol | Used by |
|-------|----------|---------|
| `AwsRestJsonAction<R>` | REST + JSON body | S3, Lambda |
| `AwsJsonAction<R>` | JSON with `X-Amz-Target` | DynamoDB, KMS |
| `AwsQueryAction` | Form-urlencoded | SNS, STS |

## Service Companion Pattern

Every Amazon service uses `AwsServiceCompanion`:

```kotlin
companion object : AwsServiceCompanion("s3") {
    fun Http(credentialsProvider, http, clock, payloadMode, overrideEndpoint): MyService
}
```

## Gotchas

- All Amazon modules include `connect-amazon-core` transitively
- Use `overrideEndpoint` parameter for LocalStack or other compatible endpoints
- Payload signing can be set to `Payload.Mode.Unsigned` for S3 large uploads
