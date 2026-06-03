---
license: Apache-2.0
module: http4k-platform-aws
---

# http4k-platform-aws Reference

AWS platform integration — request signing, SDK client adapter, and credential management.

## AWS Request Signing (Signature V4)

```kotlin
val client = ClientFilters.AwsAuth(
    scope = AwsCredentialScope("us-east-1", "s3"),
    credentials = AwsCredentials("accessKey", "secretKey")
).then(httpClient)

// With session token (STS temporary credentials)
val client = ClientFilters.AwsAuth(
    scope = AwsCredentialScope("eu-west-1", "execute-api"),
    credentials = AwsCredentials("accessKey", "secretKey", "sessionToken")
).then(httpClient)

// Dynamic credentials (refreshable)
val client = ClientFilters.AwsAuth(
    scope = AwsCredentialScope("us-east-1", "s3"),
    credentialsProvider = { loadCredentials() }
).then(httpClient)

// Unsigned payload (for streaming)
val client = ClientFilters.AwsAuth(
    scope = scope,
    credentials = credentials,
    payloadMode = Payload.Mode.Unsigned
).then(httpClient)
```

## AWS SDK Client Adapter

```kotlin
// Use http4k HttpHandler as the underlying transport for AWS SDK v2
val sdkClient = AwsSdkClient(httpHandler)

// Use with any AWS SDK v2 client
val s3 = S3Client.builder()
    .httpClient(sdkClient)
    .build()
```

## AwsCredentials

```kotlin
data class AwsCredentials(
    val accessKey: String,
    val secretKey: String,
    val sessionToken: String? = null
)
```

## Gotchas

- **Signature V4**: The `AwsAuth` filter computes HMAC-SHA256 signatures per AWS Signature V4 spec, adding `Authorization`, `x-amz-date`, and `x-amz-content-sha256` headers.
- **Session token**: When present, the `x-amz-security-token` header is added automatically.
- **S3 vs other services — canonical path encoding**: S3 single-encodes path components in the canonical request; every other AWS service double-encodes. `AwsAuth` and `AwsRequestPreSigner` apply the correct encoding automatically based on the `service` field in `AwsCredentialScope` — no manual adjustment needed.
- **SDK adapter**: `AwsSdkClient` bridges AWS SDK v2's `SdkHttpClient` to http4k, enabling testability — swap in a fake `HttpHandler` for testing.
