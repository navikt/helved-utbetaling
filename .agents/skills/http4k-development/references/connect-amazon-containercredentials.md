---
license: Apache-2.0
module: http4k-connect-amazon-containercredentials
---

# http4k-connect-amazon-containercredentials Reference

ContainerCredentials client — connect actions for ECS/EKS container credential endpoint.

## Client

```kotlin
val containerCreds = ContainerCredentials.Http(
    http = JavaHttpClient()   // optional
)
```

## Get Credentials

```kotlin
val credentials = containerCreds.getCredentials().successValue()
// Returns: AccessKeyId, SecretAccessKey, Token, Expiration, RoleArn
```

## Usage with CredentialsProvider

```kotlin
// Typically used via CredentialsProvider rather than directly
val credentialsProvider = CredentialsProvider.Container()
```

## Gotchas

- Reads `AWS_CONTAINER_CREDENTIALS_RELATIVE_URI` or `AWS_CONTAINER_CREDENTIALS_FULL_URI` env var for endpoint
- Only available within ECS tasks or EKS pods with appropriate IAM roles
- Credentials are short-lived — cache and refresh before expiry (check `Expiration` field)
- `CredentialsProvider.Container()` handles the credential refresh automatically
- Not available outside container environments — use `CredentialsProvider.Environment()` for local dev
