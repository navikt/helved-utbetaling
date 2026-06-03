---
license: Apache-2.0
module: http4k-connect-amazon-cognito
---

# http4k-connect-amazon-cognito Reference

Cognito client — connect actions for AWS Cognito user pools and identity.

## Client

```kotlin
val cognito = Cognito.Http(
    region = Region.of("us-east-1"),
    credentialsProvider = CredentialsProvider.Environment(),
    http = JavaHttpClient()   // optional
)
```

## User Pool Management

```kotlin
val poolId = cognito.createUserPool(
    poolName = PoolName.of("my-user-pool")
).successValue().UserPool.Id

cognito.listUsers(userPoolId = poolId).successValue()
cognito.adminGetUser(userPoolId = poolId, username = Username.of("user@example.com")).successValue()
```

## User Administration

```kotlin
// Create user
cognito.adminCreateUser(
    userPoolId = poolId,
    username = Username.of("user@example.com"),
    temporaryPassword = Password.of("Temp1234!"),
    userAttributes = listOf(AttributeType("email", "user@example.com"))
).successValue()

// Delete user
cognito.adminDeleteUser(userPoolId = poolId, username = Username.of("user@example.com")).successValue()
```

## Authentication

```kotlin
// Admin-initiated auth (no SRP)
val tokens = cognito.adminInitiateAuth(
    userPoolId = poolId,
    clientId = ClientId.of("client-id"),
    authFlow = AuthFlowType.ADMIN_NO_SRP_AUTH,
    authParameters = mapOf("USERNAME" to "user@example.com", "PASSWORD" to "password")
).successValue().AuthenticationResult

// User-facing auth (SRP)
cognito.initiateAuth(
    clientId = ClientId.of("client-id"),
    authFlow = AuthFlowType.USER_PASSWORD_AUTH,
    authParameters = mapOf("USERNAME" to "user@example.com", "PASSWORD" to "password")
).successValue()
```

## JWKS (Token Verification)

```kotlin
val jwks = cognito.getJwks(userPoolId = poolId).successValue()
```

## Gotchas

- Uses JSON protocol with `X-Amz-Target` header
- User pool IDs include region prefix: `us-east-1_xxxxxxxx`
- `adminInitiateAuth` requires `ALLOW_ADMIN_USER_PASSWORD_AUTH` enabled on the client
- Token verification requires fetching JWKS and validating JWT signatures
- Password policies are enforced — temporary passwords must meet complexity requirements
