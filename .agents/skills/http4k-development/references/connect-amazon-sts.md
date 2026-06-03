---
license: Apache-2.0
module: http4k-connect-amazon-sts
---

# http4k-connect-amazon-sts Reference

STS client — assume IAM roles and exchange credentials.

## Client

```kotlin
val sts = STS.Http(
    region = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient()   // optional
)
```

## Assume Role

```kotlin
val result = sts.assumeRole(
    RoleArn = ARN.of("arn:aws:iam::123456789012:role/MyRole"),
    RoleSessionName = "my-session",
    DurationSeconds = 3600,      // optional, default 1 hour
    ExternalId = "external-id",  // optional, for cross-account
    Policy = null                // optional, inline policy JSON
).successValue()

val credentials = result.AssumedRoleUser.AssumedRoleId
val tempCreds = result.Credentials
// tempCreds.AccessKeyId, SecretAccessKey, SessionToken, Expiration
```

## Using Assumed Credentials

```kotlin
val assumed = sts.assumeRole(roleArn, sessionName).successValue()
val tempCreds = assumed.Credentials

val tempProvider: CredentialsProvider = {
    AwsCredentials(
        accessKey = tempCreds.AccessKeyId.value,
        secretKey = tempCreds.SecretAccessKey.value,
        sessionToken = tempCreds.SessionToken?.value
    )
}

val s3 = S3Bucket.Http(bucketName, region, tempProvider)
```

## Get Caller Identity

```kotlin
val identity = sts.getCallerIdentity().successValue()
// identity.UserId   — "ARO123EXAMPLE123:my-role-session-name"
// identity.Account  — AwsAccount
// identity.Arn      — ARN of the assumed role/user
```

## Web Identity Federation

```kotlin
sts.assumeRoleWithWebIdentity(
    RoleArn = roleArn,
    RoleSessionName = "session",
    WebIdentityToken = "jwt-token-from-idp"
).successValue()
```

## Gotchas

- Uses **query protocol** (form-urlencoded POST) — response is XML
- `RoleSessionName` must be 2–64 characters, alphanumeric + `=,.@-_`
- Temporary credentials have expiry — cache and refresh before `Expiration`
- `ExternalId` is required for cross-account role assumptions if configured on the role
