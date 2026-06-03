---
license: Apache-2.0
module: http4k-connect-amazon-iamidentitycenter
---

# http4k-connect-amazon-iamidentitycenter Reference

IAM Identity Center client — two clients in one module: SSO for credential federation and OIDC for device authorization.

## SSO Client

```kotlin
val sso = SSO.Http(
    region = Region.of("us-east-1"),
    http = JavaHttpClient()   // optional — no credentials needed (uses token)
)
```

### Get Federated Credentials

```kotlin
val credentials = sso.getFederatedCredentials(
    accessToken = AccessToken.of("access-token-from-device-auth"),
    accountId = AccountId.of("123456789012"),
    roleName = RoleName.of("DeveloperAccess")
).successValue().RoleCredentials
```

## OIDC Client

```kotlin
val oidc = OIDC.Http(
    region = Region.of("us-east-1"),
    http = JavaHttpClient()   // optional
)
```

### Device Authorization Flow

```kotlin
// 1. Register the client application
val registration = oidc.registerClient(
    clientName = ClientName.of("my-cli-tool"),
    clientType = ClientType.PUBLIC
).successValue()

// 2. Start device authorization
val deviceAuth = oidc.startDeviceAuthorization(
    clientId = registration.ClientId,
    clientSecret = registration.ClientSecret,
    startUrl = StartUrl.of("https://my-org.awsapps.com/start")
).successValue()

println("Open: ${deviceAuth.VerificationUriComplete}")

// 3. Poll for token (after user authorizes)
val token = oidc.createToken(
    clientId = registration.ClientId,
    clientSecret = registration.ClientSecret,
    deviceCode = deviceAuth.DeviceCode,
    grantType = GrantType.DEVICE_CODE
).successValue().AccessToken
```

## Grant Types

```kotlin
GrantType.DeviceCode          // "urn:ietf:params:oauth:grant-type:device_code"
GrantType.AuthorizationCode   // "authorization_code"
GrantType.AuthorizationCode2  // "AuthorizationCode" — alternative wire value used by some SSO implementations
GrantType.RefreshToken        // "refresh_token"
GrantType.RefreshToken2       // "RefreshToken" — alternative wire value used by some SSO implementations
```

Use `GrantType.fromWire(wireValue)` to parse a grant type from a wire string — matches both the standard and alternative wire values.

## Gotchas

- SSO and OIDC are two separate clients in the same module
- SSO client does NOT use AWS SigV4 — it uses bearer token auth
- OIDC device flow: poll `createToken` until user approves (handle `AuthorizationPendingException`)
- Access tokens expire — re-run device flow to get new token
- SSO credentials have short TTL — cache and refresh before expiry
- Some AWS SSO implementations send `"AuthorizationCode"` or `"RefreshToken"` as grant type wire values (PascalCase) instead of the OAuth standard lowercase forms — use `AuthorizationCode2` / `RefreshToken2` variants when needed
