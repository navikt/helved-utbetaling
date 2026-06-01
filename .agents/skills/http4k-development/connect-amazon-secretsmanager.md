---
license: Apache-2.0
module: http4k-connect-amazon-secretsmanager
---

# http4k-connect-amazon-secretsmanager Reference

SecretsManager client — connect actions for AWS Secrets Manager.

## Client

```kotlin
val secrets = SecretsManager.Http(
    region = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient()   // optional
)
```

## Secret Lifecycle

```kotlin
// Create
val arn = secrets.createSecret(
    name = SecretName.of("my-app/db-password"),
    secretString = "supersecret",
    description = "Database password"
).successValue().ARN

// Read
val value = secrets.getSecretValue(SecretId.of("my-app/db-password")).successValue().SecretString

// Update
secrets.putSecretValue(
    secretId = SecretId.of("my-app/db-password"),
    secretString = "newsecret"
).successValue()

// List
secrets.listSecrets().successValue()

// Delete
secrets.deleteSecret(SecretId.of("my-app/db-password")).successValue()
```

## Update Secret Metadata

```kotlin
secrets.updateSecret(
    secretId = SecretId.of("my-app/db-password"),
    description = "Updated description"
).successValue()
```

## Gotchas

- Uses JSON protocol with `X-Amz-Target` header
- `getSecretValue` returns either `SecretString` or `SecretBinary` — check which is set
- Deleted secrets have a recovery window (7–30 days) before permanent deletion; use `forceDeleteWithoutRecovery = true` to skip
- Secret names can include `/` for path-like organisation (e.g. `myapp/prod/db`)
- Cross-account access requires resource-based policies attached to the secret
