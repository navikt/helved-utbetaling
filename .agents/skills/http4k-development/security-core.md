---
license: Apache-2.0
module: http4k-security-core
---

# http4k-security-core Reference

Core security abstractions. `Security` is a `Filter` — compose security policies with `.and()` (all must pass) and `.or()` (first success wins).

## Security Interface

```kotlin
interface Security {
    val filter: Filter
}
```

## ApiKeySecurity

```kotlin
// Query parameter
val security = ApiKeySecurity(Query.required("api_key"), { it == "secret" })

// Header
val security = ApiKeySecurity(Header.required("X-Api-Key"), { validateKey(it) })

// Cookie
val security = ApiKeySecurity(Cookies.required("auth"), { validateCookie(it) })

// Typed parameter
val security = ApiKeySecurity(Query.int().required("key"), { it > 0 })

// With consumer lookup (inject authenticated principal into request)
val consumerLens = RequestKey.required<User>("user")
val security = ApiKeySecurity(
    param = Header.required("X-Api-Key"),
    consumer = consumerLens,
    consumerForKey = { key -> lookupUserByKey(key) }  // returns User? — null rejects
)

// Skip auth for OPTIONS (CORS preflight) — default is true
val security = ApiKeySecurity(Query.required("key"), { true }, authorizeOptionsRequests = false)
```

## BasicAuthSecurity

```kotlin
// Static credentials
val security = BasicAuthSecurity("realm", Credentials("user", "password"))

// Credentials validator
val security = BasicAuthSecurity("realm") { creds ->
    creds.user == "admin" && creds.password == "secret"
}

// With consumer lookup
val principalLens = RequestKey.required<User>("principal")
val security = BasicAuthSecurity(
    realm = "my app",
    key = principalLens,
    lookup = { creds -> findUser(creds.user, creds.password) }  // returns User?
)
```

## BearerAuthSecurity

```kotlin
// Static token
val security = BearerAuthSecurity("my-fixed-token")

// Token validator
val security = BearerAuthSecurity { token -> validateJwt(token) }

// With consumer lookup
val principalLens = RequestKey.required<User>("principal")
val security = BearerAuthSecurity(
    key = principalLens,
    lookup = { token -> findUserByToken(token) }  // returns User?
)
```

## OAuth Security Types

For OpenAPI spec generation. The `filter` parameter handles actual authentication.

```kotlin
// Authorization Code flow
AuthCodeOAuthSecurity(
    authorizationUrl = Uri.of("https://auth.example.com/authorize"),
    tokenUrl = Uri.of("https://auth.example.com/token"),
    scopes = listOf(OAuthScope("read", "Read access"), OAuthScope("write", "Write access")),
    filter = ServerFilters.BearerAuth { validateToken(it) },
    refreshUrl = Uri.of("https://auth.example.com/refresh")
)

// Implicit flow
ImplicitOAuthSecurity(
    authorizationUrl = Uri.of("https://auth.example.com/authorize"),
    scopes = listOf(OAuthScope("read")),
    filter = ServerFilters.BearerAuth(token)
)

// Client Credentials flow
ClientCredentialsOAuthSecurity(
    tokenUrl = Uri.of("https://auth.example.com/token"),
    scopes = listOf(OAuthScope("service")),
    filter = ServerFilters.BearerAuth(token)
)

// Resource Owner Password Credentials flow
UserCredentialsOAuthSecurity(
    tokenUrl = Uri.of("https://auth.example.com/token"),
    scopes = listOf(OAuthScope("read")),
    filter = ServerFilters.BearerAuth(token)
)
```

## OpenIdConnectSecurity

```kotlin
val security = OpenIdConnectSecurity(
    discoveryUrl = Uri.of("https://auth.example.com/.well-known/openid-configuration"),
    filter = ServerFilters.BearerAuth { validateToken(it) }
)
```

## Composition

```kotlin
// AND — all must pass (filters chained sequentially)
val security = apiKeySecurity.and(basicAuthSecurity)

// OR — first success wins (returns first non-401 response)
val security = bearerSecurity.or(apiKeySecurity)

// Complex
val security = (bearerSecurity.or(basicAuthSecurity)).and(rateLimitSecurity)
```

## NoSecurity

```kotlin
val security = NoSecurity  // passes all requests through
```

## Using Security as a Filter

```kotlin
// Wrap a handler
val protected = security.filter.then { Response(OK).body("secret") }

// Access injected consumer in handler
val principalLens = RequestKey.required<User>("principal")
val security = BearerAuthSecurity(key = principalLens, lookup = ::findUser)
val app = security.filter.then { req ->
    val user = principalLens(req)
    Response(OK).body("Hello ${user.name}")
}
```

## Gotchas

- **Security is just a Filter**: All security types wrap a `Filter` that returns `401 UNAUTHORIZED` on failure.
- **Consumer lookup returns null to reject**: When using the consumer/key/lookup pattern, returning `null` rejects the request.
- **OR stops at first success**: `OrSecurity` tries each in order and returns the first non-`401` response.
- **AND chains filters**: `AndSecurity` chains all filters sequentially — each sees the request modified by previous ones.
- **OAuth types are for spec generation**: The OAuth security classes primarily exist for OpenAPI spec rendering. The `filter` parameter does the actual authentication work.
- **OPTIONS bypass**: `ApiKeySecurity` skips auth for OPTIONS requests by default (for CORS preflight). Set `authorizeOptionsRequests = false` to disable.
