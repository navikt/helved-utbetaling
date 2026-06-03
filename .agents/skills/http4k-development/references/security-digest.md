---
license: Apache-2.0
module: http4k-security-digest
---

# http4k-security-digest Reference

HTTP Digest Authentication (RFC 2617) with server and client filters. Supports MD5, QoP auth, nonce tracking, and proxy mode.

## Server Filter

`nonceGenerator` and `nonceVerifier` are **required** parameters — there are no defaults.

```kotlin
val app = ServerFilters.DigestAuth(
    realm = "my-app",
    passwordLookup = { username -> passwords[username] },  // returns password String? — null rejects
    nonceGenerator = Nonce.SECURE_NONCE,
    nonceVerifier = { nonce -> isValidNonce(nonce) }
).then { Response(OK).body("authenticated") }
```

### Server with All Options

```kotlin
val app = ServerFilters.DigestAuth(
    realm = "my-app",
    passwordLookup = { username -> findPasswordForUser(username) },
    qop = listOf(Qop.Auth),
    digestMode = DigestMode.Standard,           // or DigestMode.Proxy
    nonceGenerator = Nonce.SECURE_NONCE,
    nonceVerifier = { nonce -> isValidNonce(nonce) },  // validate nonce freshness
    algorithm = "MD5",
    usernameKey = RequestKey.required("user")    // inject username into request context
).then { req ->
    val username = usernameKey(req)
    Response(OK).body("Hello $username")
}
```

## Client Filter

```kotlin
// Static credentials — client automatically responds to 401 challenges
val client = ClientFilters.DigestAuth(Credentials("admin", "password"))
    .then(httpClient)

val response = client(Request(GET, "/protected"))  // handles challenge/response automatically
```

### Client with Options

```kotlin
val client = ClientFilters.DigestAuth(
    credentials = Credentials("admin", "password"),
    nonceGenerator = Nonce.SECURE_NONCE,       // client nonce generation
    digestMode = DigestMode.Standard           // or DigestMode.Proxy
).then(httpClient)
```

### Dynamic Credentials

```kotlin
val client = ClientFilters.DigestAuth(
    credentials = { Credentials("admin", currentPassword()) },
    nonceGenerator = Nonce.SECURE_NONCE
).then(httpClient)
```

## End-to-End Example

```kotlin
// Server — nonceGenerator and nonceVerifier are required
val server = ServerFilters.DigestAuth(
    realm = "my-realm",
    passwordLookup = { if (it == "admin") "password" else null },
    nonceGenerator = Nonce.SECURE_NONCE,
    nonceVerifier = { true }
).then { Response(OK).body("secret content") }

// Client authenticates automatically
val response = ClientFilters.DigestAuth(Credentials("admin", "password"))
    .then(server)(Request(GET, "/"))

// response.status == OK, body == "secret content"
```

## Proxy Mode

```kotlin
// Uses Proxy-Authenticate / Proxy-Authorization headers instead of
// WWW-Authenticate / Authorization
val server = ServerFilters.DigestAuth(
    "realm", passwordLookup, digestMode = DigestMode.Proxy
).then(handler)

val client = ClientFilters.DigestAuth(
    credentials, digestMode = DigestMode.Proxy
).then(server)
```

## Qop (Quality of Protection)

```kotlin
Qop.Auth      // authentication only (most common)
Qop.AuthInt   // authentication + integrity checking
```

## Gotchas

- **`nonceGenerator` and `nonceVerifier` are required**: There are no defaults — both must be explicitly provided when calling `ServerFilters.DigestAuth`. This prevents accidentally shipping with trivial or insecure nonce verification.
- **Password lookup returns plain password**: The server needs the raw password to compute the digest. It does not receive or store hashed passwords.
- **Client handles challenge automatically**: `ClientFilters.DigestAuth` intercepts `401` responses, extracts the challenge, computes the digest, and retries the request.
- **Nonce count tracking**: The client tracks nonce reuse and increments `nc` (nonce count) for the same server nonce. A new server nonce resets the count.
- **Timing-safe comparison**: Digest verification uses `MessageDigest.isEqual()` for constant-time comparison to prevent timing attacks.
- **MD5 default**: The algorithm defaults to MD5 per RFC 2617. Other algorithms can be specified as a string.
