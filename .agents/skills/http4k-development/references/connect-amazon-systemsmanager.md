---
license: Apache-2.0
module: http4k-connect-amazon-systemsmanager
---

# http4k-connect-amazon-systemsmanager Reference

SystemsManager client — connect actions for AWS Systems Manager Parameter Store.

## Client

```kotlin
val ssm = SystemsManager.Http(
    region = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient()   // optional
)
```

## Parameter Operations

```kotlin
// Put parameter
ssm.putParameter(
    name = ParameterName.of("/myapp/config/db-url"),
    value = "jdbc:postgresql://host/db",
    type = ParameterType.SecureString
).successValue()

// Get single parameter
val param = ssm.getParameter(
    name = ParameterName.of("/myapp/config/db-url"),
    withDecryption = true
).successValue().Parameter

// Get multiple parameters
val params = ssm.getParameters(
    names = listOf(
        ParameterName.of("/myapp/config/db-url"),
        ParameterName.of("/myapp/config/db-user")
    ),
    withDecryption = true
).successValue().Parameters

// Delete
ssm.deleteParameter(name = ParameterName.of("/myapp/config/db-url")).successValue()
```

## Gotchas

- Uses JSON protocol with `X-Amz-Target` header
- `SecureString` parameters require KMS key for encryption/decryption
- `withDecryption = true` required to get plaintext values for SecureString
- Path-based names (starting with `/`) enable hierarchical organisation and path-based queries
- `getParameters` returns invalid parameter names in `InvalidParameters` list — check it
